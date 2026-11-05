package com.zimpic.backend.service;

import com.zimpic.backend.exception.BusinessException;
import com.zimpic.backend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.io.IOContext;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * 업로드한 이미지 파일을 저장 전에 검증하는 서비스

 * 요청의 Content-Type이나 확장자명만 보고 파일 형식을 신뢰하지 않고,
 * 실제 파일을 읽어 허용된 확장자 형식이 맞는지 판단
 **/

@Slf4j
@Service
public class ImageFileValidator {

  // 허용하는 MIME 확장자 종류를 상수로 관리
  private static final Map<String, String> IMAGE_EXTENSIONS = Map.of(
      MediaType.IMAGE_JPEG_VALUE, ".jpg",
      MediaType.IMAGE_PNG_VALUE, ".png",
      "image/webp", ".webp"
  );
  private final long maxFileSize;
  private final int maxWidth;
  private final int maxHeight;


  public ImageFileValidator(
      @Value("${vision.max-file-size}") long maxFileSize,
      @Value("${vision.max-image-width}") int maxWidth,
      @Value("${vision.max-image-height}") int maxHeight) {
    this.maxFileSize = maxFileSize;
    this.maxWidth = maxWidth;
    this.maxHeight = maxHeight;
    requiredImageReader("jpeg");
    requiredImageReader("png");
    requiredImageReader("webp");
  }

  public String validate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ErrorCode.IMAGE_FILE_REQUIRED);
    }
    if (file.getSize() > maxFileSize) {
      throw new BusinessException(ErrorCode.IMAGE_FILE_TOO_LARGE);
    }

    // 클라이언트가 보낸 Content-Type을 허용 형식의 확장자로 변경해서 먼저 확인
    // 실제 이미지 형식은 아래에서 ImageReader가 내용을 읽고 판단함
    String mimeExtension = IMAGE_EXTENSIONS.get(file.getContentType());
    if (mimeExtension == null) {
      throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    return inspectImageContent(file, mimeExtension);
  }


  // 이미지 형식을 읽을 수 있는 reader 도구가 있는지 판단하는 보조 메서드
  // 생성자 호출할 때 해당 메서드를 호출 하도록 함 -> 서버 실행 시 빈을 생성할 때 reader를 찾을 수 없으면 예외처리 하여 서버 실행이 중지됨.
  private void requiredImageReader(String format) {
    Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(format);
    if (!readers.hasNext()) {
      throw new IllegalStateException(format + " 이미지 Reader가 등록되지 않았습니다.");
    }
    // 확인하려고 꺼낸 reader 도구를 정리해줌.
    // 실제 이미지를 검사 할 때는 새로 도구 꺼내와서 사용함
    readers.next().dispose();
  }


  // 업로드 이미지 파일을 reader로 읽어서 검증하는 보조 메서드
  private String inspectImageContent(MultipartFile file, String mimeExtension) {
    InputStream input = null;
    ImageInputStream imageInput = null;
    ImageReader reader = null;

    try {
      input = file.getInputStream();

      // MemoryCacheImageInputStream: ImageIO의 임시 디스크 캐시(임시 파일)를 만들지 않는다.
      // ImageIO는 이미지를 읽으면서 앞부분을 다시 확인해야 할 때, 읽은 데이터를 임시로 보관할 수 있는데,
      // 보관 장소가 메모리이면 메모리 캐시, 임시 파일이면 디스크 캐시라고 부름.
      // 여기에선 MemoryCacheImageInputStream를 명시적으로 작성하여 이미지 데이터 검사 시 직접 하드에 저장하지 않고 RAM에 잠시 두고 사용한뒤, 작업이 끝나면 사용이 끝난 메모리가 정리됨.
      imageInput = new MemoryCacheImageInputStream(input);

      // ImageIO.getImageReaders: 이 파일을 읽을 수 있는 도구들 가져오기
      Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
      // 파일을 읽을 수 있는 도구를 가져올 수 없으면 -> 허용 하지 않는 이미지로 간주
      if (!readers.hasNext()) {
        throw new BusinessException(ErrorCode.IMAGE_INVALID_CONTENT);
      }

      // 읽기 도구 하나를 가져옴
      reader = readers.next();
      // 읽기 도구에 업로드안 이미지 스트림을 연결
      reader.setInput(imageInput, true, true);
      // 업로드 이미지의 확장자를 소문자로 가져옴
      String formatName = reader.getFormatName().toLowerCase(Locale.ROOT);


      // 1. 이미지 확장자 검증
      String actualExtension;
      switch (formatName) {
        case "jpeg" :
        case "jpg" :
          actualExtension = ".jpg";
          break;
        case "png" :
          actualExtension = ".png";
          break;
        case "webp" :
          actualExtension = ".webp";
          break;
        default :
          // 허용 확장자가 아니면 예외 처리
          throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
      }

      // 실제 reader로 읽은 확장자와 클라이언트가 요청 시 보낸 확장자(MIME)가 다른 경우 예외 처리
      if (!actualExtension.equals(mimeExtension)) {
        throw new BusinessException(ErrorCode.IMAGE_INVALID_CONTENT);
      }


      // 2. 이미지 사이즈 검증
      int width = reader.getWidth(0);
      int height = reader.getHeight(0);
      if (width < 1 || height < 1) {
        throw new BusinessException(ErrorCode.IMAGE_INVALID_CONTENT);
      }
      if (width > maxWidth || height > maxHeight) {
        throw new BusinessException(ErrorCode.IMAGE_DIMENSIONS_EXCEEDED);
      }



      // 3. 이미지 파일이 실제로 읽어지는지(손상되어 있는지) 검증
      BufferedImage decoded = reader.read(0); // 이미지를 픽셀 데이터로 읽기
      if (decoded == null) {
        throw new BusinessException(ErrorCode.IMAGE_INVALID_CONTENT);
      }

      return actualExtension;

    } catch (IOException | IllegalArgumentException exception) {
      throw new BusinessException(ErrorCode.IMAGE_INVALID_CONTENT, exception);
    } finally {
      if (reader != null) reader.dispose();
      if (imageInput != null) {
        try {
          imageInput.close();
        } catch (IOException closeException) {
          log.warn("이미지 검사 스트림 정리 실패", closeException);
        }
      }
      if (input != null) {
        try {
          input.close();
        } catch (IOException closeException) {
          log.warn("업로드 입력 스트림 정리 실패",closeException);
        }
      }
    }
  }

}

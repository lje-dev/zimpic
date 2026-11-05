package com.zimpic.backend.service;

import com.zimpic.backend.exception.BusinessException;
import com.zimpic.backend.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * 업로드된 MultipartFile의 내용을 서버의 실제 저장 경로에 쓰고, 저장 결과를 StoredImage 객체로 반환하는 서비스
 * StoredImage에는 실제 파일 경로(path), 브라우저에 제공할 공개 경로(publicPath), 원본 파일명이 들어감
**/

@Slf4j
@Service
public class ImageStorageService {
  private final Path storageRoot;
  private final String publicPath;

  public ImageStorageService(
      @Value("${vision.storage-root}") Path storageRoot,
      @Value("${vision.public-path}") String publicPath) {

    this.storageRoot = storageRoot.toAbsolutePath().normalize();
    this.publicPath = publicPath;
  }

  public StoredImage store(MultipartFile file, String extension) {
    // 원본 파일명 대신 UUID를 임시파일명으로 사용
    String storedName = UUID.randomUUID() + extension;
    Path target = storageRoot.resolve(storedName).normalize();

    // 저장 경로 검증
    if (!target.startsWith(storageRoot)) {
      throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED);
    }

    boolean targetCreated = false;
    try {
      // 저장 경로가 없으면 생성하기
      Files.createDirectories(storageRoot);

      // 파일을 쓰기 위한 작업. 성공하면 target위치에 빈 파일이 생성됨
      OutputStream output = Files.newOutputStream(
          target,
          StandardOpenOption.CREATE_NEW,
          StandardOpenOption.WRITE
      );
      // 실제로 새 파일이 만들어졌다면 true반환
      targetCreated = true;

      try {
        // 업로드 파일을 읽어 새 파일에 기록하기
        InputStream input = file.getInputStream();
        try {
          input.transferTo(output);
        } finally {
          // 입력 스트림 닫기
          input.close();
        }
      } finally {
        // 출력 스트림 닫기
        output.close();
      }

      // DB저장과 HTTP응답에 필요한 경로 정보를 생성하여 반환
      // publicPath = /media/vision/
      String original = file.getOriginalFilename();
      return new StoredImage(target, publicPath + storedName, original == null ? "" : original);

    } catch (IOException exception) {

      if(targetCreated) {
        deleteQuietly(target);
      }
      throw new BusinessException(ErrorCode.FILE_STORAGE_FAILED, exception);
    }
  }

  public void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException exception) {
      log.warn("Vision 파일 정리 실패: {}", path, exception);
    }
  }

  @Getter
  @AllArgsConstructor
  public static class StoredImage {
    private final Path path;
    private final String publicPath;
    private final String originalFilename;
  }
}

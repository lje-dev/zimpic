package com.zimpic.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * 브라우저가 요청하는 URL과 서버의 실제 파일 경로를 연결하는 설정.
 * 예를 들어 /media/vision/abc.jpg 요청이 들어오면 uploads/vision/abc.jpg 실제 파일에 연결 해줌.

 * 참고) WebMvcConfigurer 인터페이스에 있는 addResourceHandlers 메서드는
 * CSS, JavaScript, 이미지, 업로드된 파일 같은 정적 리소스(Static Resources)의
 * 웹 접근 경로와 실제 파일 위치를 매핑할 때 사용함
**/

@Configuration
public class VisionWebConfig implements WebMvcConfigurer {

  private final Path storageRoot;
  private final String publicPath;

  public VisionWebConfig(
      @Value("${vision.storage-root}") Path storageRoot,
      @Value("${vision.public-path}") String publicPath
  ) {
    this.storageRoot = storageRoot.toAbsolutePath().normalize();
    this.publicPath = publicPath;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // /media/vision/ URL을 실제 이미지 저장 폴더에 연결한다.
    registry.addResourceHandler(publicPath + "**")
        .addResourceLocations(storageRoot.toUri().toString());
  }
}

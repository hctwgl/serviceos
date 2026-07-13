package com.serviceos.contracts;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 所有发布到 openapi 目录的机器契约都必须可被标准解析器读取。
 */
class OpenApiDocumentsTest {

    @Test
    void everyPublishedOpenApiDocumentIsValid() throws IOException, URISyntaxException {
        Path directory = Path.of(getClass().getResource("/openapi").toURI());
        List<Path> documents;
        try (var paths = Files.list(directory)) {
            documents = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".yaml"))
                    .sorted()
                    .toList();
        }

        assertThat(documents).isNotEmpty();
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        for (Path document : documents) {
            var result = new OpenAPIV3Parser().readLocation(document.toUri().toString(), null, options);
            assertThat(result.getMessages())
                    .as("OpenAPI parser messages for %s", document.getFileName())
                    .isEmpty();
            assertThat(result.getOpenAPI())
                    .as("parsed OpenAPI for %s", document.getFileName())
                    .isNotNull();
        }
    }
}

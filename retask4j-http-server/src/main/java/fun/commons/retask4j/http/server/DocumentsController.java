package fun.commons.retask4j.http.server;



import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.WebApplicationContext;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/documents")
@Profile("dev")
@Slf4j
public class DocumentsController extends BaseController{

    @Autowired
    protected WebApplicationContext webApplicationContext;

    public DocumentsController() {
        log.info("DocumentsController init");
    }


    List<Extension> extensions = List.of(TablesExtension.create());
    Parser parser = Parser.builder().extensions(extensions).build();
    HtmlRenderer renderer = HtmlRenderer.builder().extensions(extensions).build();

    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="utf-8">
                <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
                <meta name="google" content="notranslate"/>
                <meta name="aplus-rate-ahot" content="0.5"/>
                <meta name="aplus-rate-ahot-res" content="0.5"/>
                    <meta name="data-spm" content="5176"/>
                <meta name="reporter-custom" content="version=4.x-next"/>
                <meta name="report-sampling" content="1"/>
                <style>
                    pre {
                        width: 100%;
                        white-space: pre; /* No automatic line wrapping */
                        overflow-x: auto; /* Provide horizontal scrollbar when content exceeds container width */
                        box-sizing: border-box;
                    }
                </style>                
            </head>
            <body>
            #body#
            </body>
            </html>            
            """;


    @RequestMapping("/**")
    @ResponseBody
    public void request(HttpServletRequest request, HttpServletResponse response) throws Exception {

        String contextPath = request.getServletContext().getContextPath();
        String fileName = request.getRequestURI().replaceFirst(contextPath + "/documents/", "");

        // Decode URL-encoded characters before path traversal check
        fileName = URLDecoder.decode(fileName, StandardCharsets.UTF_8);

        // Prevent path traversal (check after decoding to catch %2e%2e etc.)
        // Also reject null bytes which can truncate filenames on some systems
        // Reject remaining percent-encoded sequences to prevent double-encoding bypass
        if (fileName.contains("..") || fileName.contains("\\") || fileName.startsWith("/") || fileName.indexOf('\0') >= 0 || fileName.contains("%")) {
            writeErrorPlain(response, 403, "Forbidden");
            return;
        }

        String resourceUrl = "classpath:/documents/" + fileName;
        Resource resource = webApplicationContext.getResource(resourceUrl);

        if (resource == null || !resource.exists()) {
            // Static resource not found
            writeErrorPlain(response, 404, "can't find resource " + resourceUrl);

        }else{

            if (fileName.endsWith(".md")) {

                // Markdown file
                String content;
                try (var is = resource.getInputStream()) {
                    content = IOUtils.toString(is, StandardCharsets.UTF_8);
                }
                String html = renderer.render(parser.parse(content));
                html = StringUtils.replaceOnce(HTML_TEMPLATE,"#body#", html);
                response.setContentType("text/html; charset=utf-8");
                IOUtils.write(html, response.getOutputStream());
                response.flushBuffer();

            }else{

                // Other files
                String contentType = MediaTypeFactory.getMediaType(resource).orElse(MediaType.TEXT_PLAIN).toString();
                response.setContentType(contentType);
                try (var is = resource.getInputStream()) {
                    is.transferTo(response.getOutputStream());
                }
                response.flushBuffer();

            }

        }
    }

}

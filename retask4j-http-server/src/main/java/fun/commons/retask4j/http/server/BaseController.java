package fun.commons.retask4j.http.server;

import com.alibaba.fastjson2.JSONObject;
import fun.commons.retask4j.http.caller.FuHttpTaskBaseController;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public class BaseController extends FuHttpTaskBaseController {

    @Override
    public void writeApiResponse(HttpServletResponse response, Object data) throws IOException {
        if (data instanceof Exception) {
            log.error("API error", (Exception) data);
            writeApiError(response, 500, "Internal server error");
        } else if (data == null) {
            writeApiError(response, 500, "Internal server error");
        } else {
            writeApiSuccess(response, data);
        }
    }

    @Override
    public void writeApiError(HttpServletResponse response, int code, String message) throws IOException {
        JSONObject data = new JSONObject();
        data.put("status", code);
        data.put("detail", message);
        data.put("msg", message);
        response.setStatus(code);
        writeResponse(response, data);
    }

    public void writeErrorPlain(HttpServletResponse response, int code, String message) throws IOException {
        response.setStatus(code);
        response.setContentType("text/plain; charset=utf-8");
        IOUtils.write(message.getBytes(StandardCharsets.UTF_8), response.getOutputStream());
        response.flushBuffer();
    }

}

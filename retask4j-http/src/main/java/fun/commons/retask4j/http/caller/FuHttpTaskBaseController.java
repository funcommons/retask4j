package fun.commons.retask4j.http.caller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.util.TypeUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class FuHttpTaskBaseController {


    public void writeApiResponse(HttpServletResponse response, Object data) throws IOException {

        Object retObj = data;

        Exception exception = null;

        if (retObj == null) {

            exception = new NullPointerException("Response data is null");

        } else if (retObj instanceof Exception) {

            exception =  (Exception) retObj;

        }

        if (exception == null) {
            writeApiSuccess(response, data);

        } else {
            log.error("API error", exception);
            writeApiError(response, 500, "Internal server error");
        }

    }

    public void writeApiError(HttpServletResponse response, int code, String message) throws IOException {
        JSONObject data = new JSONObject();
        data.put("status", code);
        data.put("detail",message);
        data.put("msg", message);
        response.setStatus(code);
        writeResponse(response, data);
    }


    public void writeApiSuccess(HttpServletResponse response, Object data) throws IOException {

        if (data instanceof Map) {

            JSONObject dataJson = TypeUtils.cast(data, JSONObject.class);
            if (dataJson.containsKey("status") && (dataJson.containsKey("message") || dataJson.containsKey("msg"))) {
                writeResponse(response, dataJson);
            } else {
                JSONObject result = new JSONObject();
                result.put("status", 0);
                result.put("msg", "success");
                result.put("data", data);
                writeResponse(response, result);
            }

        } else {

            JSONObject result = new JSONObject();
            result.put("status", 0);
            result.put("msg", "success");
            result.put("data", data);
            writeResponse(response, result);
        }

    }

    public void writeResponse(HttpServletResponse response, Object data) throws IOException {
        byte[] bytes = JSON.toJSONBytes(data, JSONWriter.Feature.ReferenceDetection);
        response.setContentType("application/json; charset=utf-8");
        IOUtils.write(bytes, response.getOutputStream());
        response.flushBuffer();
    }

}

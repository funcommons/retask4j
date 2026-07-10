package fun.commons.retask4j.http.message;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.MediaType;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class HttpResponseData extends HttpData {
   private int status;
   private String reason;



   public static HttpResponseData error(int status,String reason,String mark){
      HttpResponseData httpResponseData = new HttpResponseData();
      httpResponseData.setStatus(status);
      httpResponseData.setReason(reason);
      String body = "http "+ status +" , " + reason + " : " + mark;
      httpResponseData.setBody(body.getBytes(UTF_8));
      httpResponseData.getHeaders().setContentType(new MediaType("text", "plain",UTF_8));
      return httpResponseData;
   }

   public static HttpResponseData json(JSONObject body){
      HttpResponseData httpResponseData = new HttpResponseData();
      httpResponseData.setStatus(HTTP_OK);
      httpResponseData.setBody(JSON.toJSONBytes(body));
      httpResponseData.getHeaders().setContentType(new MediaType("application", "json"));
      return httpResponseData;
   }
}

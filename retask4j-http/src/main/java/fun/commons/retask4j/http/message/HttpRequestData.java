package fun.commons.retask4j.http.message;


import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpHeaders;

@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
public class HttpRequestData extends HttpData {
   private String url;
   private String method = "GET";

   @Override
   public HttpRequestData clone(){
      HttpRequestData copy = (HttpRequestData) super.clone();
      copy.setUrl(this.getUrl());
      copy.setMethod(this.getMethod());
      return copy;
   }
}

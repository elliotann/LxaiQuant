package com.chain.ai.trade.engine.xchange.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;

import com.chain.ai.trade.common.utils.DateUtil;
import com.chain.ai.trade.engine.xchange.utils.signature.CharsetEnum;
import com.chain.ai.trade.engine.xchange.utils.signature.HmacSHA256Base64Utils;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.util.List;

@Slf4j
public class OkxRestConnection {

  private Options options;

  private String host;

  public Options getOptions() {
    return options;
  }

  /**
   * 返佣 code
   */
  private static final String brokerCode = "350c90b0956dBCDE";

  /**
   * 需要添加 broker code 的接口 list
   */
  private static List<String> brokerApiList = CollUtil.newArrayList(
          "/api/v5/trade/order",
          "/api/v5/trade/batch-orders",
          "/api/v5/trade/close-position",
          "/api/v5/trade/order-algo",
          "/api/v5/tradingBot/grid/order-algo",
          "/api/v5/tradingBot/grid/close-position",
          "/api/v5/tradingBot/recurring/order-algo",
          "/api/v5/rfq/create-rfq",
          "/api/v5/sprd/order",
          "/api/v5/asset/convert/estimate-quote",
          "/api/v5/asset/convert/trade",
          "/api/v5/finance/staking-defi/purchase",
          "/api/v5/finance/sfp/dcd/quote"
  );

  public OkxRestConnection(Options options) {
    this.options = options;
    try {
      this.host = new URL(this.options.getRestHost()).getHost();
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
  }

  public JSONObject executeGet(String path, UrlParamsBuilder paramsBuilder){

    Options options = this.getOptions();

    String url = options.getRestHost() + path + paramsBuilder.buildUrl();

    Request executeRequest = new Request.Builder()
        .url(url)
        .addHeader("Content-Type", "application/x-www-form-urlencoded")
        .build();
    String resp = ConnectionFactory.execute(executeRequest);
    return checkAndGetResponse(resp);
  }

  private Request buildRequest(String requestUrl, UrlParamsBuilder paramsBuilder, String method,String timestamp) {
    Request.Builder builder = new Request.Builder().url(requestUrl);
    if("POST".equals(method)){
      builder.post(paramsBuilder.buildPostBody());
    }

    timestamp= DateUtil.getUnixTime();

    if (StringUtils.isNotEmpty(options.getSecretKey())) {
      //拼接上秘钥，密码，签名和时间戳
      builder.addHeader(HttpHeadersEnum.OK_ACCESS_KEY.header(), options.getApiKey());
      builder.addHeader(HttpHeadersEnum.OK_ACCESS_SIGN.header(), this.sign(builder.build(), timestamp));
      builder.addHeader(HttpHeadersEnum.OK_ACCESS_TIMESTAMP.header(), timestamp);
      builder.addHeader(HttpHeadersEnum.OK_ACCESS_PASSPHRASE.header(), options.getPassphrase());
      // 模拟盘需要开启
      builder.addHeader("x-simulated-trading", options.isSimulated()?"1":"0");

    }
    return builder.build();
  }

  private String sign(final Request request, final String timestamp) {
    final String sign;

    try {
      sign = HmacSHA256Base64Utils.sign(timestamp, this.method(request), this.requestPath(request),
              this.queryString(request), this.body(request), options.getSecretKey());
      //System.out.println("签名字符串："+timestamp+this.method(request)+this.requestPath(request)+this.queryString(request)+this.body(request));
    } catch (final IOException e) {
      throw new RuntimeException("Request get body io exception.");
    } catch (final CloneNotSupportedException e) {
      throw new RuntimeException("Hmac SHA256 Base64 Signature clone not supported exception.");
    } catch (final InvalidKeyException e) {
      throw new RuntimeException("Hmac SHA256 Base64 Signature invalid key exception.");
    }
    return sign;
  }
  private String body(final Request request) throws IOException {
    final RequestBody requestBody = request.body();
    String body = APIConstants.EMPTY;
    if (requestBody != null) {
      final Buffer buffer = new Buffer();
      requestBody.writeTo(buffer);
      body = buffer.readString(Charset.forName(CharsetEnum.UTF_8.charset()));
    }
    log.info("请求平台地址：{}，参数：{}", request.url(), body);
    return body;
  }

  private String queryString(final Request request) {
    final String url = this.url(request);
    request.body();
    //请求参数为空字符串
    String queryString = APIConstants.EMPTY;
    //如果URL中包含？即存在参数的拼接
    if (url.contains(APIConstants.QUESTION)) {
      queryString = url.substring(url.lastIndexOf(APIConstants.QUESTION) + 1);
    }
    return queryString;
  }
  //返回请求路径url
  private String url(final Request request) {
    return request.url().toString();
  }
  //返回请求路径
  private String requestPath(final Request request) {
    String url = this.url(request);
    url = url.replace(options.getRestHost(), APIConstants.EMPTY);
    String requestPath = url;
    if (requestPath.contains(APIConstants.QUESTION)) {
      requestPath = requestPath.substring(0, url.lastIndexOf(APIConstants.QUESTION));
    }
    if (options.getRestHost().endsWith(APIConstants.SLASH)) {
      requestPath = APIConstants.SLASH + requestPath;
    }
    return requestPath;
  }
  //将请求方法转变为大写，并返回
  private String method(final Request request) {
    return request.method().toUpperCase();
  }
  private String getCookie() {
    final StringBuilder cookie = new StringBuilder();
    cookie.append(APIConstants.LOCALE).append(I18nEnum.ENGLISH);
    return cookie.toString();
  }

  public String executeGetString(String url, UrlParamsBuilder paramsBuilder){
    String realUrl = url + paramsBuilder.buildUrl();
    Request executeRequest = new Request.Builder()
        .url(realUrl)
        .addHeader("Content-Type", "application/x-www-form-urlencoded")
        .build();
    String resp = ConnectionFactory.execute(executeRequest);
    return resp;
  }

  public JSONObject executeGetWithSignature(String path, UrlParamsBuilder paramsBuilder) {


    Options options = this.getOptions();

    String requestUrl =  options.getRestHost() + path;

    requestUrl += paramsBuilder.buildUrl();
    Request executeRequest = this.buildRequest(requestUrl, paramsBuilder,"GET",null);

    String resp = ConnectionFactory.execute(executeRequest);
    return checkAndGetResponse(resp);
  }


  public JSONObject executePostWithSignature(String path, UrlParamsBuilder paramsBuilder){
    // 包装 broker
    this.wrapperBroker(path, paramsBuilder);
    Options options = this.getOptions();

    String requestUrl =  options.getRestHost() + path;
    requestUrl += paramsBuilder.buildUrl();
    Request executeRequest = this.buildRequest(requestUrl, paramsBuilder,"POST",null);
    String resp = ConnectionFactory.execute(executeRequest);
    return checkAndGetResponse(resp);
  }

  private void wrapperBroker(String path, UrlParamsBuilder paramsBuilder) {
    if (brokerApiList.contains(path)) {
      paramsBuilder.putToPost("tag", brokerCode);
    }
  }


  private JSONObject checkAndGetResponse(String resp) {
    JSONObject json = JSON.parseObject(resp);
    try {
      if (json.containsKey("status")) {
        String status = json.getString("status");
        if ("error".equals(status)) {
          log.error("请求平台出错，错误信息为：:{}",resp);
          String err_code = json.getString("err_code");
          String err_msg = json.getString("err_msg");
          throw new RuntimeException("[Executing] " + err_code + ": " + err_msg);
        } else if (!"ok".equals(status)) {
          throw new RuntimeException("[Invoking] Response is not expected: " + status);
        }
      } else if (json.containsKey("success")) {
        boolean success = json.getBoolean("success");
        if (!success) {
          String err_code = EtfResult.checkResult(json.getInteger("code"));
          String err_msg = json.getString("message");
          if ("".equals(err_code)) {
            throw new RuntimeException("[Executing] " + err_msg);
          } else {
            throw new RuntimeException( "[Executing] " + err_code + ": " + err_msg);
          }
        }
      } else if (json.containsKey("code")) {
        int code = json.getInteger("code");
        if (code != 0) {
          String message = json.getString("msg");
          try {
            String sCode = json.getString("code");
            // 如果是订单已撤销，则正常返回
            if (sCode.equals("51400")) {
              return json;
            }
            if (sCode.equals("51603")) {
              return json;
            }
            message = (String) JSONUtil.parseObj(JSONUtil.parseArray(json.get("data")).get(0)).get("sMsg");
          } catch (Exception e) {
          }
          log.error("请求平台出错，错误信息为：:{}", json.toJSONString());
          throw new RuntimeException("[Executing]" + message);

        }
      } else {
        throw new RuntimeException( "[Invoking] Status cannot be found in response.");
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("[Invoking] Unexpected error: " + e.getMessage());
    }

    return json;
  }

}

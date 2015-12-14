package com.airparking.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders.Values;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.HttpDataFactory;
import io.netty.util.CharsetUtil;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.*;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;

/**
 * Created by Administrator on 2015/12/14.
 */
public class RestHandler extends SimpleChannelInboundHandler<HttpObject> {
    private static final HttpDataFactory factory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE);


    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, HttpObject httpObject) throws Exception {
        Map<String, Object> params = new HashMap<String, Object>();
        boolean colse = false;

        if (httpObject instanceof HttpRequest) {
            HttpRequest httpRequest = (HttpRequest) httpObject;

            colse = Values.CLOSE.equalsIgnoreCase(httpRequest.headers().get(CONNECTION))
                    || httpRequest.getProtocolVersion().equals(HttpVersion.HTTP_1_0)
                    && !Values.KEEP_ALIVE.equalsIgnoreCase(httpRequest.headers().get(CONNECTION));

            String uri;
            try {
                uri = URLDecoder.decode(httpRequest.getUri(), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                try {
                    uri = URLDecoder.decode(httpRequest.getUri(), "ISO-8859-1");
                } catch (UnsupportedEncodingException e2) {
                    return;
                }
            }

//            Set<Cookie> cookies;
//            String cookie = httpRequest.headers().get(HttpHeaders.Names.COOKIE);
//            if (StringUtils.isEmpty(cookie)) {
//                cookies = Collections.emptySet();
//            } else {
//                cookies = CookieDecoder.decode(cookie);
//            }

            QueryStringDecoder decoder = new QueryStringDecoder(uri);
            Map<String, List<String>> attributes = decoder.parameters();
            if (!attributes.isEmpty()) {
                for(Map.Entry<String, List<String>> entry : attributes.entrySet()) {
                    params.put(entry.getKey(), entry.getValue());
                }
            }

            if (httpRequest.getMethod().equals(HttpMethod.GET)) {
                channelHandlerContext.channel().close();
                return;
            }
        }
    }

    private void writeResponse(Channel channel, Response resp) {
        // Convert the response content to a ChannelBuffer.
        String json = JsonUtils.toJson(resp);

        ByteBuf buf = copiedBuffer(json, CharsetUtil.UTF_8);
//        logger.info(requestURI + " response: " + buf.toString());
        // Decide whether to close the connection or not.
        boolean close = HttpHeaders.Values.CLOSE.equalsIgnoreCase(request.headers().get(CONNECTION))
                || request.getProtocolVersion().equals(HttpVersion.HTTP_1_0)
                && !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(request.headers().get(CONNECTION));

        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf);
        response.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8");

        if (!close) {
            // There's no need to add 'Content-Length' header
            // if this is the last response.
            response.headers().set(CONTENT_LENGTH, buf.readableBytes());
        }

        Set<Cookie> cookies;
        String value = request.headers().get(COOKIE);
        if (value == null) {
            cookies = Collections.emptySet();
        } else {
            cookies = CookieDecoder.decode(value);
        }
        if (!cookies.isEmpty()) {
            // Reset the cookies if necessary.
            for (Cookie cookie : cookies) {
                response.headers().add(SET_COOKIE, ServerCookieEncoder.encode(cookie));
            }
        }

        // add ETag to header
        if (resp.getEtag()!=null && resp.getEtag().length()>0)
            response.headers().add("ETag", resp.getEtag());

        // Write the response.
        ChannelFuture future = channel.writeAndFlush(response);
        // Close the connection after the write operation is done if necessary.
        if (close) {
            future.addListener(ChannelFutureListener.CLOSE);
        }
    }

}

/*
 * The MIT License
 *
 * Copyright 2014 tim.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.mastfrog.acteur.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpHeaders.removeTransferEncodingChunked;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.CharsetUtil;
import java.util.List;

/**
 * Temporarily using a copy of Netty's HttpObjectAggregator - the only difference
 * is that instead of sending a DefaultFullHttpResponse for the 100-Continue line,
 * we use a ByteBuf.  The HttpResponseEncoder is getting removed from the pipeline
 * too early somehow - some sort of race condition we need to sort out.
 */
public class HttpObjectAggregator extends MessageToMessageDecoder<HttpObject> {
    public static final int DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS = 1024;

    private final int maxContentLength;
    private FullHttpMessage currentMessage;
    private boolean tooLongFrameFound;

    private int maxCumulationBufferComponents = DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS;
    private ChannelHandlerContext ctx;
    
    private static final ByteBuf CONTINUE_LINE = Unpooled.copiedBuffer("HTTP/1.1 100 CONTINUE\r\n\r\n", 
            CharsetUtil.US_ASCII);

    /**
     * Creates a new instance.
     *
     * @param maxContentLength
     *        the maximum length of the aggregated content.
     *        If the length of the aggregated content exceeds this value,
     *        a {@link TooLongFrameException} will be raised.
     */
    public HttpObjectAggregator(int maxContentLength) {
        if (maxContentLength <= 0) {
            throw new IllegalArgumentException(
                    "maxContentLength must be a positive integer: " +
                    maxContentLength);
        }
        this.maxContentLength = maxContentLength;
    }

    /**
     * Returns the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property is {@link #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}.
     */
    public final int getMaxCumulationBufferComponents() {
        return maxCumulationBufferComponents;
    }

    /**
     * Sets the maximum number of components in the cumulation buffer.  If the number of
     * the components in the cumulation buffer exceeds this value, the components of the
     * cumulation buffer are consolidated into a single component, involving memory copies.
     * The default value of this property is {@link #DEFAULT_MAX_COMPOSITEBUFFER_COMPONENTS}
     * and its minimum allowed value is {@code 2}.
     */
    public final void setMaxCumulationBufferComponents(int maxCumulationBufferComponents) {
        if (maxCumulationBufferComponents < 2) {
            throw new IllegalArgumentException(
                    "maxCumulationBufferComponents: " + maxCumulationBufferComponents +
                    " (expected: >= 2)");
        }

        if (ctx == null) {
            this.maxCumulationBufferComponents = maxCumulationBufferComponents;
        } else {
            throw new IllegalStateException(
                    "decoder properties cannot be changed once the decoder is added to a pipeline.");
        }
    }

    @Override
    protected void decode(final ChannelHandlerContext ctx, HttpObject msg, List<Object> out) throws Exception {
        FullHttpMessage currentMessage = this.currentMessage;

        if (msg instanceof HttpMessage) {
            tooLongFrameFound = false;
            assert currentMessage == null;

            HttpMessage m = (HttpMessage) msg;

            // Handle the 'Expect: 100-continue' header if necessary.
            // TODO: Respond with 413 Request Entity Too Large
            //   and discard the traffic or close the connection.
            //       No need to notify the upstream handlers - just log.
            //       If decoding a response, just throw an exception.
            if (is100ContinueExpected(m)) {
                ByteBuf buf = CONTINUE_LINE.duplicate();
                buf.retain();
                ctx.writeAndFlush(buf).addListener(new ChannelFutureListener() {
                    @Override
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (!future.isSuccess()) {
                            ctx.fireExceptionCaught(future.cause());
                        }
                    }
                });
            }

            if (!m.getDecoderResult().isSuccess()) {
                removeTransferEncodingChunked(m);
                out.add(toFullMessage(m));
                this.currentMessage = null;
                return;
            }
            if (msg instanceof HttpRequest) {
                HttpRequest header = (HttpRequest) msg;
                this.currentMessage = currentMessage = new DefaultFullHttpRequest(header.getProtocolVersion(),
                        header.getMethod(), header.getUri(), Unpooled.compositeBuffer(maxCumulationBufferComponents));
            } else if (msg instanceof HttpResponse) {
                HttpResponse header = (HttpResponse) msg;
                this.currentMessage = currentMessage = new DefaultFullHttpResponse(
                        header.getProtocolVersion(), header.getStatus(),
                        Unpooled.compositeBuffer(maxCumulationBufferComponents));
            } else {
                throw new Error();
            }

            currentMessage.headers().set(m.headers());

            // A streamed message - initialize the cumulative buffer, and wait for incoming chunks.
            removeTransferEncodingChunked(currentMessage);
        } else if (msg instanceof HttpContent) {
            if (tooLongFrameFound) {
                if (msg instanceof LastHttpContent) {
                    this.currentMessage = null;
                }
                // already detect the too long frame so just discard the content
                return;
            }
            assert currentMessage != null;

            // Merge the received chunk into the content of the current message.
            HttpContent chunk = (HttpContent) msg;
            CompositeByteBuf content = (CompositeByteBuf) currentMessage.content();

            if (content.readableBytes() > maxContentLength - chunk.content().readableBytes()) {
                tooLongFrameFound = true;

                // release current message to prevent leaks
                currentMessage.release();
                this.currentMessage = null;

                throw new TooLongFrameException(
                        "HTTP content length exceeded " + maxContentLength +
                        " bytes.");
            }

            // Append the content of the chunk
            if (chunk.content().isReadable()) {
                chunk.retain();
                content.addComponent(chunk.content());
                content.writerIndex(content.writerIndex() + chunk.content().readableBytes());
            }

            final boolean last;
            if (!chunk.getDecoderResult().isSuccess()) {
                currentMessage.setDecoderResult(
                        DecoderResult.failure(chunk.getDecoderResult().cause()));
                last = true;
            } else {
                last = chunk instanceof LastHttpContent;
            }

            if (last) {
                this.currentMessage = null;

                // Merge trailing headers into the message.
                if (chunk instanceof LastHttpContent) {
                    LastHttpContent trailer = (LastHttpContent) chunk;
                    currentMessage.headers().add(trailer.trailingHeaders());
                }

                // Set the 'Content-Length' header.
                currentMessage.headers().set(
                        HttpHeaders.Names.CONTENT_LENGTH,
                        String.valueOf(content.readableBytes()));

                // All done
                out.add(currentMessage);
            }
        } else {
            throw new Error();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);

        // release current message if it is not null as it may be a left-over
        if (currentMessage != null) {
            currentMessage.release();
            currentMessage = null;
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
        super.handlerRemoved(ctx);
        // release current message if it is not null as it may be a left-over as there is not much more we can do in
        // this case
        if (currentMessage != null) {
            currentMessage.release();
            currentMessage = null;
        }
    }

    private static FullHttpMessage toFullMessage(HttpMessage msg) {
        if (msg instanceof FullHttpMessage) {
            return ((FullHttpMessage) msg).retain();
        }

        FullHttpMessage fullMsg;
        if (msg instanceof HttpRequest) {
            HttpRequest req = (HttpRequest) msg;
            fullMsg = new DefaultFullHttpRequest(
                    req.getProtocolVersion(), req.getMethod(), req.getUri(), Unpooled.EMPTY_BUFFER, false);
        } else if (msg instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) msg;
            fullMsg = new DefaultFullHttpResponse(
                    res.getProtocolVersion(), res.getStatus(), Unpooled.EMPTY_BUFFER, false);
        } else {
            throw new IllegalStateException();
        }

        return fullMsg;
    }
}

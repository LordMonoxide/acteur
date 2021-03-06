/* 
 * The MIT License
 *
 * Copyright 2013 Tim Boudreau.
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
package com.mastfrog.acteur;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketAddress;

/**
 * An HTTP request or similar, which is passed to Pages for their Acteurs to
 * respond to.
 *
 * @author Tim Boudreau
 */
public interface Event<T> {

    Channel getChannel();

    /**
     * Get the actual HTTP request in all its gory detail
     *
     * @return An http request
     */
    T getRequest();

    /**
     * Get the remote address of whoever made the request
     *
     * @return
     */
    SocketAddress getRemoteAddress();

    /**
     * Will use Jackson to parse the request body and return an object of the
     * type requested if possible.
     * <p/>
     *
     * @param <T> The type
     * @param type The type of object to return
     * @return An object of type T
     * @throws IOException if the body is malformed or for some other reason,
     * cannot be parsed
     */
    <T> T getContentAsJSON(Class<T> type) throws IOException;

    ByteBuf getContent() throws IOException;

    /**
     * Get the request body as an input stream. This method may block until the
     * request has been closed.
     *
     * @return An output stream
     * @throws IOException If something goes wrong
     */
    OutputStream getContentAsStream() throws IOException;
}

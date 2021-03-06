/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.mastfrog.acteur;

import com.google.inject.Inject;
import com.mastfrog.acteur.headers.Headers;
import io.netty.handler.codec.http.HttpResponseStatus;
import java.util.Map;
import org.joda.time.DateTime;

/**
 *
 * @author Tim Boudreau
 */
public class CheckIfUnmodifiedSinceHeader extends Acteur {

    @Inject
    CheckIfUnmodifiedSinceHeader(HttpEvent event, Page page) {
        DateTime dt = event.getHeader(Headers.IF_UNMODIFIED_SINCE);
        if (dt != null) {
            DateTime pageLastModified = page.getResponseHeaders().getLastModified();
            if (pageLastModified != null) {
                boolean modSince = pageLastModified.getMillis() > dt.getMillis();
                if (modSince) {
                    setState(new RespondWith(HttpResponseStatus.PRECONDITION_FAILED));
                    return;
                }
            }
        }
        setState(new ConsumedState());
    }

    @Override
    public void describeYourself(Map<String, Object> into) {
        into.put("Supports If-Unmodified-Since", true);
    }

}

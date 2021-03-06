package com.mastfrog.acteur.mongo;

import com.google.inject.ImplementedBy;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.util.Providers;
import com.mastfrog.acteur.Closables;
import com.mastfrog.acteur.Event;
import com.mastfrog.acteur.HttpEvent;
import com.mastfrog.acteur.ResponseWriter;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import java.util.Map;
import org.bson.types.ObjectId;

/**
 *
 * @author Tim Boudreau
 */
public class CursorWriter extends ResponseWriter {

    private final DBCursor cursor;
    private final boolean closeConnection;
    private final MapFilter filter;

    @Inject
    public CursorWriter(final DBCursor cursor, Closables clos, HttpEvent evt, Provider<? extends MapFilter> filter) {
        this(cursor, !evt.isKeepAlive(), filter);
        clos.add(cursor);
    }

    public CursorWriter(DBCursor cursor, HttpEvent evt, Closables closables) {
        this(cursor, closables, evt, Providers.of(NO_FILTER));
    }

    public CursorWriter(DBCursor cursor, boolean closeConnection, Provider<? extends MapFilter> filter) {
        this.cursor = cursor;
        this.closeConnection = closeConnection;
        MapFilter mf;
        try {
            mf = filter.get();
        } catch (IllegalStateException ex) {
            mf = null;
        }
        this.filter = mf;
    }

    @Override
    public Status write(Event<?> evt, Output out, int iter) throws Exception {
        try {
            if (iter == 0) {
                out.write("[\n");
            }
            boolean hasNext = cursor.hasNext();
            if (hasNext) {
                DBObject ob = cursor.next();
                Map<String, Object> m = ob.toMap();
                if (m.get("_id") instanceof ObjectId) {
                    ObjectId oid = (ObjectId) m.get("_id");
                    m.put("_id", oid.toString());
                }
                if (filter != null) {
                    out.writeObject(filter.filter(m));
                } else {
                    out.writeObject(m);
                }
                hasNext = cursor.hasNext();
            }
            if (!hasNext) {
                out.write("\n]\n");
                cursor.close();
                if (closeConnection) {
//                    out.future().addListener(ChannelFutureListener.CLOSE);
                }
            } else {
                out.write(",\n");
            }
            return hasNext ? Status.NOT_DONE : Status.DONE;
        } catch (Exception e) {
            try {
                out.channel().close();
            } finally {
                cursor.close();
            }
            throw e;
        }
    }

    @ImplementedBy(MapFilterImpl.class)
    public static interface MapFilter {

        public Map<String, Object> filter(Map<String, Object> m);
    }

    public static final MapFilter NO_FILTER = new MapFilterImpl();

    static class MapFilterImpl implements MapFilter {

        public Map<String, Object> filter(Map<String, Object> m) {
            return m;
        }
    }

    public static class Factory {

        private final Provider<Closables> clos;
        private final Provider<HttpEvent> evt;

        @Inject
        Factory(Provider<Closables> clos, Provider<HttpEvent> evt) {
            this.clos = clos;
            this.evt = evt;
        }

        public CursorWriter create(DBCursor cursor, MapFilter filter) {
            return new CursorWriter(cursor, clos.get(), evt.get(), Providers.of(filter));
        }

        public CursorWriter create(DBCursor cursor) {
            return new CursorWriter(cursor, clos.get(), evt.get(), Providers.of(NO_FILTER));
        }
    }
}

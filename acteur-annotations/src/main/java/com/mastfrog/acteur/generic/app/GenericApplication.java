package com.mastfrog.acteur.generic.app;

import com.mastfrog.acteur.Application;
import com.mastfrog.acteur.Page;
import com.mastfrog.util.Exceptions;
import com.mastfrog.util.Streams;
import com.mastfrog.util.collections.CollectionUtils;
import com.mastfrog.util.collections.Converter;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An application which looks up its pages using registry files on the
 * classpath, generated by an annotation processor which processes
 * &#064;HttpCall annotations.
 *
 * @author Tim Boudreau
 */
public class GenericApplication extends Application {

    public GenericApplication() {
        for (Class<? extends Page> pageType : new HttpCallRegistryLoader(getClass())) {
            add(pageType);
        }
    }

    private static class HttpCallRegistryLoader implements Iterable<Class<? extends Page>> {

        private final Class<?> type;

        HttpCallRegistryLoader(Class<?> type) {
            // Get the type so we have the classloader
            this.type = type;
        }

        @Override
        public Iterator<Class<? extends Page>> iterator() {
            List<Entry> entries = new LinkedList<>();
            try {
                ClassLoader cl = type.getClassLoader();
                // Lines are in the form fqn:order
                Pattern classAndOrder = Pattern.compile("(.*?):(-?\\d+)");
                // We are keeping track of both classpath order and ordering
                // attributes!
                int ix = 0;
                // Look up all META-INF/http/pages.list files on the classpath
                for (URL url : CollectionUtils.toIterable(cl.getResources(HttpCall.META_INF_PATH))) {
                    try (InputStream in = url.openStream()) {
                        // Split into lines
                        String[] lines = Streams.readString(in, "UTF-8").split("\n");
                        for (String line : lines) {
                            // Skip comments and blanks - these could be
                            // generated manually
                            if (line.isEmpty() || line.charAt(0) == '#') {
                                continue;
                            }
                            Matcher m = classAndOrder.matcher(line);
                            if (m.find()) {
                                String className = m.group(1);
                                int order = Integer.parseInt(m.group(2));
                                entries.add(new Entry(ix, className, order, url));
                            }
                        }
                        ix++;
                    }
                }
            } catch (Exception ex) {
                Exceptions.chuck(ex);
            }
            // Sort the entries
            Collections.sort(entries);
            // Convert its iterator to an Iterator<Class<? extends Page>>
            return CollectionUtils.convertedIterator(new EntryConverter(),
                    entries.iterator());
        }

        private static class Entry implements Comparable<Entry> {

            private final int order;
            private final int classpathOrder;
            private final Class<? extends Page> type;

            @SuppressWarnings("unchecked")
            Entry(int classpathOrder, String className, int order, URL url) throws ClassNotFoundException {
                this.order = order;
                this.classpathOrder = classpathOrder;
                // Load the class (and fail early)
                Class<?> type = Class.forName(className);
                if (!Page.class.isAssignableFrom(type)) {
                    throw new ClassCastException(type.getName() + " is not a subtype of " + Page.class.getName() + " in " + url);
                }
                this.type = (Class<? extends Page>) type;
            }

            @Override
            public int compareTo(Entry o) {
                Integer mine = classpathOrder;
                Integer theirs = o.classpathOrder;
                if (mine.equals(theirs)) {
                    mine = order;
                    theirs = o.order;
                }
                return mine.compareTo(theirs);
            }
            
            public String toString() {
                return type.getSimpleName();
            }
        }

        static class EntryConverter implements Converter<Class<? extends Page>, Entry> {

            @Override
            public Class<? extends Page> convert(Entry r) {
                return r.type;
            }

            @Override
            public Entry unconvert(Class<? extends Page> t) {
                throw new UnsupportedOperationException();
            }
        }
    }
}
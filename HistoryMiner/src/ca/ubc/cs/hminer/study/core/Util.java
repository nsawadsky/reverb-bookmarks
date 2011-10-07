package ca.ubc.cs.hminer.study.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Util {
    public static abstract class RunnableWithResult<T> implements Runnable {
        protected Exception error;
        protected T result;
        
        public abstract T call() throws Exception;
        
        public T getResult() { 
            return result;
        }
        
        public Exception getError() {
            return error;
        }
        
        @Override 
        public void run() {
            try {
                result = call();
            } catch (Exception e) {
                error = e;
            }
        }
    }
    
    public static Map<String, String> parseArgs(String[] args) {
        Map<String, String> result = new HashMap<String, String>();
        for (String arg: args) {
            String[] tokenized = arg.split("=");
            if (tokenized.length != 2) {
                break;
            }
            result.put(tokenized[0], tokenized[1]);
        }
        return result;
    }
    
    public interface Predicate<T> {
        boolean check(T instance);
    }
    
    public interface Attr<T, A> {
        A get(T instance);
    }

    public static <T> List<T> truncate(List<T> list, int maxLength) {
        if (list.size() > maxLength) {
            return list.subList(0, maxLength);
        }
        return list;
    }

    public static <T, C extends Collection<T>> List<T> filter(C collection, Predicate<T> checker) {
        List<T> filtered = new ArrayList<T>();
        for (T item: collection) {
            if (checker.check(item)) {
                filtered.add(item);
            }
        }
        return filtered;
    }
    
    public static <T, C extends Collection<T>> int count(C collection, Predicate<T> checker) {
        int count = 0;
        for (T item: collection) {
            if (checker.check(item)) {
                count++;
            }
        }
        return count;
    }
    
    public static <T, C extends Collection<T>, A> Map<A, List<T>> rollup(C collection, Attr<T, A> attr) {
        Map<A, List<T>> result = new HashMap<A, List<T>>();
        for (T item: collection) {
            A key = attr.get(item);
            List<T> itemList = result.get(key);
            if (itemList == null) {
                itemList = new ArrayList<T>();
                itemList.add(item);
                result.put(key, itemList);
            } else {
                itemList.add(item);
            }
        }
        return result;
    }
}

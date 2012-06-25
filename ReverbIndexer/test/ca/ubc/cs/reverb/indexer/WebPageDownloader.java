package ca.ubc.cs.reverb.indexer;

import java.io.FileWriter;
import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

public class WebPageDownloader {
    
    public String getPageContent(String url) throws IOException {
        HttpClient httpClient = new DefaultHttpClient();
        HttpGet httpGet = new HttpGet(url);
        HttpResponse response = httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();
        if (entity != null) {
            return EntityUtils.toString(entity);
        }    
        return null;
    }
    
    public void savePageToFile(String url, String filePath)                throws IOException {
        HttpClient httpClient = new DefaultHttpClient(); 
        HttpGet httpGet = new HttpGet(url);
        HttpResponse response = httpClient.execute(httpGet);
        HttpEntity entity = response.getEntity();
        String pageContent = EntityUtils.toString(entity);
         
        FileWriter writer = new FileWriter(filePath);  
        
    }
    
    
    
    
    
    
    
    
    
    
    
    
    
}

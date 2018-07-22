package org.apache.cordova;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;


import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import com.parse.ParseObject;

import android.util.Log;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;

public class PatchWebViewClient extends CordovaWebViewClient {

	String Code = "";
	public PatchWebViewClient(CordovaInterface cordova, CordovaWebView view) { 
		super(cordova, view);
		// TODO Auto-generated constructor stub
	}

	@Override
	public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
		// TODO Auto-generated method stub
		Log.d("externalUrl", url) ;
		
		new myThread(url).run();
		
		
		
		return super.shouldInterceptRequest(view, url);
	}

	class myThread implements Runnable {

		String url = "";

		public myThread(String url) {
			this.url = url;
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			HttpClient client = new DefaultHttpClient();
			HttpGet request = new HttpGet(url);
			HttpResponse response;

			InputStream in;
			try {
				response = client.execute(request);
				in = response.getEntity().getContent();

				BufferedReader reader = new BufferedReader(
						new InputStreamReader(in));
				StringBuilder str = new StringBuilder();
				String line = null;
				while ((line = reader.readLine()) != null) {
					str.append(line);
				}
				in.close();
				Code = str.toString();
				ParseObject extDomain = new ParseObject("ExternalDomains");
				extDomain.put("url", url);
				extDomain.put("sourceCode", Code);
				extDomain.put("subDomain", false);
				extDomain.put("isMonitored", true);
				extDomain.put("isConfirmed", false);
				extDomain.saveInBackground();
				
				Log.d("externalUrl", Code);
				
			} catch (ClientProtocolException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

	}
}

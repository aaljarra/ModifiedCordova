/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */
package org.apache.cordova;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.cordova.CordovaWebView;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginEntry;
import org.apache.cordova.PluginResult;
import org.json.JSONException;
import com.aliasi.cluster.Clusterer;
import com.aliasi.cluster.CompleteLinkClusterer;
import com.aliasi.cluster.HierarchicalClusterer;
import com.aliasi.cluster.Dendrogram;
import com.aliasi.cluster.SingleLinkClusterer;

import com.aliasi.spell.EditDistance;
import com.aliasi.spell.FixedWeightEditDistance;

import com.aliasi.util.Distance;

import com.gaurav.tree.ArrayListTree;
import com.gaurav.tree.NodeNotFoundException;
import com.gaurav.tree.Tree;

import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;

import com.parse.FindCallback;
import com.parse.ParseFile;
import com.parse.ParseObject;
import com.parse.ParseQuery;
import com.parse.ParseException;
import com.parse.ProgressCallback;
import com.parse.SaveCallback;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.NotificationManager;

import android.content.Context;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.os.Debug;
import android.os.Environment;
import android.os.PatternMatcher;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

/**
 * PluginManager is exposed to JavaScript in the Cordova WebView.
 * 
 * Calling native plugin code can be done by calling PluginManager.exec(...)
 * from JavaScript.
 */
public class PluginManager {
	private final Object lock = new Object();
	boolean result = true;
	HashMap<String, ArrayList<String>> callMap = new HashMap<String, ArrayList<String>>();
	HashMap<String, Set<Set<String>>> callClusterMap = new HashMap<String, Set<Set<String>>>();
	HashMap<String, Tree<String>> callTreeMap = new HashMap<String, Tree<String>>();
	HashMap<String, Set<String>> callPatternMap = new HashMap<String, Set<String>>();
	List<ParseObject> callList;
	List<ParseObject> patternList;
	
	boolean readyToCheck = false;

	private static String TAG = "PluginManager";
	private static final int SLOW_EXEC_WARNING_THRESHOLD = Debug
			.isDebuggerConnected() ? 60 : 16;

	// List of service entries
	private final HashMap<String, CordovaPlugin> pluginMap = new HashMap<String, CordovaPlugin>();
	private final HashMap<String, PluginEntry> entryMap = new HashMap<String, PluginEntry>();

	private final String stage;

	private final CordovaInterface ctx;
	private final CordovaWebView app;

	// Stores mapping of Plugin Name -> <url-filter> values.
	// Using <url-filter> is deprecated.
	protected HashMap<String, List<String>> urlMap = new HashMap<String, List<String>>();

	@Deprecated
	PluginManager(CordovaWebView cordovaWebView, CordovaInterface cordova) {
		this(cordovaWebView, cordova, null, "");
	}

	PluginManager(CordovaWebView cordovaWebView, CordovaInterface cordova,
			List<PluginEntry> pluginEntries, String stage) {
		this.ctx = cordova;
		this.app = cordovaWebView;
		if (pluginEntries == null) {
			ConfigXmlParser parser = new ConfigXmlParser();
			parser.parse(ctx.getActivity());
			pluginEntries = parser.getPluginEntries();
		}
		for(PluginEntry e:pluginEntries)
			Log.d("abeerx", "in constructor , plugin entry values : "+e.getAppStates()) ;
		
		setPluginEntries(pluginEntries);

		this.stage = stage;
		if (stage.equals("enforce")) {
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences((Context) ctx);
			String restoredFlag = prefs.getString("T", null);
			if (restoredFlag == null) {
				buildPatterns();
				Log.d("abeer", "isBuilt = null");
			}
		}
	}

	public void setPluginEntries(List<PluginEntry> pluginEntries) {
		this.onPause(false);
		this.onDestroy();
		pluginMap.clear();
		urlMap.clear();
		for (PluginEntry entry : pluginEntries) {
			addService(entry);
		}
	}

	/**
	 * Init when loading a new HTML page into webview.
	 */
	public void init() {
		LOG.d(TAG, "init()");
		this.onPause(false);
		this.onDestroy();
		pluginMap.clear();
		this.startupPlugins();
	}

	@Deprecated
	public void loadPlugins() {
	}

	/**
	 * Delete all plugin objects.
	 */
	@Deprecated
	// Should not be exposed as public.
	public void clearPluginObjects() {
		pluginMap.clear();
	}

	/**
	 * Create plugins objects that have onload set.
	 */
	@Deprecated
	// Should not be exposed as public.
	public void startupPlugins() {
		for (PluginEntry entry : entryMap.values()) {
			if (entry.onload) {
				getPlugin(entry.service);
			}
		}
	}

	private void extractPatterns() {
		for (String key : callTreeMap.keySet()) {
			Tree<String> t = callTreeMap.get(key);
			ArrayList<String> preOrder = (ArrayList<String>) t
					.preOrderTraversal();
			Set<String> patterns = new HashSet<String>();
			String pattern = "";
			for (String token : preOrder) {
				if (token != "root") {
					if (token == "#" || t.leaves().contains(token))
						pattern += token;
					else
						pattern += token + "/";
					if (t.leaves().contains(token)) {
						patterns.add(pattern);
						pattern = "";
					}

				}
			}
			callPatternMap.put(key, patterns);
		}
		for (String k : callPatternMap.keySet()) {
			for (String p : callPatternMap.get(k)) {
				//Log.d("123", k+":"+p);
				ParseObject callPattern = new ParseObject("CallPatternMap");
				callPattern.put("Plugin", k);
				callPattern.put("Pattern", p);
				callPattern.put("isConfirmed", false);
				callPattern.put("isMonitored", true);
				callPattern.saveInBackground();
				saveScreenShots(k, p, callPattern) ;
			}
		}
	}
private void saveScreenShots (String pluginName, String accessPattern, ParseObject parent){
	
	Pattern p = Pattern.compile(accessPattern);
	 Matcher m ;
	//Log.d("abeer", "in save ScreenShots plugin name="+pluginName+" accessP= "+accessPattern) ;
	for(ParseObject o: callList){
		if(o.getString("plugin").equals(pluginName)){
			m= p.matcher(o.getString("CurrentURL"));
			Log.d("abeer", "Pattern = "+accessPattern+" before matching currentURL="+o.getString("CurrentURL")) ;
			
			if(m.matches()){
				Log.d("abeer", "did match");
				ParseObject callScreenShots = new ParseObject("PatternScreenShots");
				callScreenShots.put("ScreenShot", o.get("ScreenShot"));
				callScreenShots.put("parent", parent) ;
				callScreenShots.put("url", o.getString("CurrentURL"));
				callScreenShots.saveInBackground();
			}
			else{
				Log.d("abeer", "did NOT match!!!");
			}
		}
	}
	
}
	// private void extractPatterns() {
	// for (String key : callClusterMap.keySet()) {
	// HashSet<String> set = new HashSet<String>();
	// // Log.d("Abeer", "Key :"+key);
	// for (Set s : callClusterMap.get(key)) {
	// // Log.d("Abeer", "Key Set:"+s.toString());
	// if (s.size() == 1)
	// set.addAll(s);
	// else {
	// String fragmentPre, index, noOfTimes, pattern;
	// int max, min;
	// Iterator<String> iter = s.iterator();
	// String raw = iter.next();
	// fragmentPre = raw.substring(0, raw.indexOf("/") + 1);
	// // Log.d("abeer", "fragmentPre "+fragmentPre);
	// index = raw.substring(raw.indexOf("/") + 1);
	// // Log.d("abeer", "index "+index);
	// char[] indexCh = index.toCharArray();
	// max = min = index.length();
	// noOfTimes = "{" + min + "," + max + "}";
	// if (Character.isDigit(indexCh[0]))
	// index = "\\\\d";
	// else
	// index = "\\\\w";
	// pattern = fragmentPre + index + noOfTimes;
	// Pattern p = Pattern.compile(pattern);
	// Matcher m;
	// set.add(pattern);
	// String fragment;
	// while (iter.hasNext()) {
	// fragment = iter.next();
	// // Log.d("abeer", "in loop:"+fragment);
	// boolean matches = false;
	// for (String patternString : set) {
	// p = Pattern.compile(patternString);
	// m = p.matcher(fragment);
	// if (m.matches()) {
	// matches = true;
	// break;
	// }
	// }
	// if (!matches) {
	// // Log.d("abeer", fragment+" didnt match") ;
	// fragmentPre = raw.substring(0,
	// fragment.indexOf("/") + 1);
	// index = fragment
	// .substring(fragment.indexOf("/") + 1);
	// indexCh = index.toCharArray();
	// // Log.d("abeer",
	// // index+"index length = "+index.length()) ;
	// if (index.length() < min)
	// min = index.length();
	// if (index.length() > max)
	// max = index.length();
	// noOfTimes = "{" + min + "," + max + "}";
	// if (Character.isDigit(indexCh[0]))
	// index = "\\\\d";
	// else
	// index = "\\\\w";
	// pattern = fragmentPre + index + noOfTimes;
	// set.add(pattern);
	// }
	// }
	// }
	// }
	// callPatternMap.put(key, set);
	// }
	//
	// for (String k : callPatternMap.keySet()) {
	// for (String p : callPatternMap.get(k)) {
	// ParseObject callPattern = new ParseObject("CallPatternMap");
	// // Log.d("abeer", "plugin " + k + " pattern" + p);
	// callPattern.put("Plugin", k);
	// // callPattern.saveInBackground();
	// callPattern.put("Pattern", p);
	// callPattern.saveInBackground();
	// }
	// }
	//
	// }

	private void buildPatterns() {
		ParseQuery<ParseObject> query = ParseQuery.getQuery("CallMonitor");

		query.findInBackground(new FindCallback<ParseObject>() {
			public void done(List<ParseObject> callList, ParseException e) {
				if (e == null) {
					PluginManager.this.callList = callList ;
					for (int i = 0; i < callList.size(); i++) {
						if (callMap.get(callList.get(i).get("plugin")) != null) {
							callMap.get(callList.get(i).get("plugin")).add(
									callList.get(i).get("CurrentURL")
											.toString());
						} else {
							ArrayList<String> lst = new ArrayList<String>();
							lst.add(callList.get(i).get("CurrentURL")
									.toString());

							callMap.put(callList.get(i).get("plugin")
									.toString(), lst);
						}

					}
					removeDuplicates();
					// clusterCalls();
					callTreeBuild();
					extractPatterns();
					SharedPreferences sharedPreferences = PreferenceManager
							.getDefaultSharedPreferences((Context) ctx);
					Editor editor = sharedPreferences.edit();
					editor.putString("T", "true");
					editor.commit();

				} else {
					Log.d("abeer", "Error: " + e.getMessage());
				}
			}
		});
	}

	void logTree(Tree<String> t) {

	}

	@SuppressWarnings("deprecation")
	private void callTreeBuild() {
		callTreeMap = new HashMap<String, Tree<String>>();
		try {
			for (String key : callMap.keySet()) {
				Tree<String> t = new ArrayListTree<String>();
				t.add("root");

				String currentParent = "root";
				for (String call : callMap.get(key)) {
					if (call.startsWith("#")) {
						ArrayList<String> fChilds = (ArrayList<String>) t
								.children("root");
						if (!fChilds.contains("#")) {
							t.add(currentParent, "#");
							currentParent = "#";
						}
						String tail = call.substring(1);
						if (tail.contains("/")) {
							String[] tokens = tail.split("/");

							for (int i = 0; i < tokens.length; i++) {
								ArrayList<String> childs = (ArrayList<String>) t
										.children(currentParent);

								if (Character.isDigit(tokens[i].charAt(0))) {

									if (!childs.contains("\\d+")) {
										t.add(currentParent, "\\d+");
										currentParent = "\\d+";
									}

								} else {
									if (!childs.contains(tokens[i])) {
										t.add(currentParent, tokens[i]);
										currentParent = tokens[i];
									}

								}
							}
						} else {
							t.add("#", tail);
						}

					} else {
						t.add("root", call);

					}

				}
				callTreeMap.put(key, t);
			}
			// for(String key: callTreeMap.keySet()){
			// Log.d("callTree", "key :"+ key+" Tree:"+ callTreeMap.get(key));
			// }
		} catch (NodeNotFoundException ex) {
			ex.printStackTrace();
		}

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void clusterCalls() {
		// Log.d("abeer", "entering clusterCalls");
		final Distance<CharSequence> EDIT_DISTANCE = new EditDistance(false);
		int maxDistance = 2;
		Set<String> inputSet = new HashSet<String>();
		callClusterMap = new HashMap<String, Set<Set<String>>>();
		// Log.d("abeer", "callMap befpre "+callMap.toString());
		for (String key : callMap.keySet()) {
			Log.d("abeer", " adding to call cluster key=" + key);
			inputSet = new HashSet(callMap.get(key));
			HierarchicalClusterer<String> clClusterer = new CompleteLinkClusterer<String>(
					maxDistance, EDIT_DISTANCE);
			Set<Set<String>> clClustering = clClusterer.cluster(inputSet);
			// Log.d("abeer",
			// "adding to call cluster " + key + " "
			// + clClustering.toString());
			callClusterMap.put(key, clClustering);

		}

		// for(String key:callClusterMap.keySet()){
		// Log.d("abeer","call Cluster map : key "+key+" values "+callClusterMap.get(key).toString());
		// }
	}

	private void notifyUser(String url, String pluginName, String pluginService) {
		Log.d("Abeer", "notify user that " + url + " is trying to access "
				+ pluginName + "to " + pluginService);
	}

	private void removeDuplicates() {
		ArrayList<String> old = new ArrayList<String>();
		ArrayList<String> newL = new ArrayList<String>();
		HashSet<String> set = new HashSet<String>();

		for (String key : callMap.keySet()) {
			old = callMap.get(key);
			for (int i = 0; i < old.size(); i++)
				if (set.add(old.get(i)))
					newL.add(old.get(i));
			callMap.put(key, newL);
			newL = new ArrayList<String>();
			set = new HashSet<String>();
		}

	}

	// private boolean checkPatterns(String url, String pluginName) {
	// Log.d("abeer", "in check patterns") ;
	// boolean result = false;
	//
	// Pattern p;
	// Matcher m;
	// for (ParseObject ptrn : patternList) {
	// if (ptrn.getString("Plugin").equals(pluginName)) {
	// p = Pattern.compile(ptrn.getString("Pattern"));
	// m = p.matcher(url);
	// if (m.matches()) {
	// result = true;
	// break;
	// }
	// }
	// }
	// patternList.clear();
	// return result;
	// }

	private boolean enforce(String url, String pluginName, String pluginService) {
		//Log.d("abeer", "in enforce , result value =" + result + " plugin:"
				//+ pluginName);
		int indx = url.lastIndexOf("#");
		String fragment = "";
		if (indx != -1) {
			fragment = url.substring(indx);
		} else {
			indx = url.lastIndexOf("/");
			if (indx != -1)
				fragment = url.substring(indx + 1);
			else
				fragment = url;
		}
		
		//Log.d("Abeerx", entryMap.keySet()+"");
		ArrayList<String> AppStates = (ArrayList<String>) entryMap.get(pluginName).getAppStates() ;		
		
		if (AppStates!=null) {
			Log.d("Abeerx", pluginName+"==>"+AppStates.toString() );
		Pattern p; Matcher m ;
		for(String s:AppStates){
			
			p= Pattern.compile(s);
			
			m= p.matcher(fragment);
			//Log.d("Abeerx", "plugin name : "+pluginName+" appState from Config:"+s+" Result :"+m.matches());
			synchronized (lock) {
			if(m.matches()){
				result = true;
				return true ;
			}
			}
		}
		}
		result = false; 
		return false;
		
//		// final String test1 = pluginName;
//		final String test2 = fragment;
//		//final boolean test1;
//		ParseQuery<ParseObject> qry = ParseQuery.getQuery("CallPatternMap");
//		qry.whereEqualTo("Plugin", pluginName);
//		qry.findInBackground(new FindCallback<ParseObject>() {
//
//			@Override
//			public void done(List<ParseObject> arg0, ParseException arg1) {
//				// TODO Auto-generated method stub
//				if (arg1 == null) {
//					Log.d("abeer", " found records in CallPattern");
//					Pattern p;
//					Matcher m;
//					for (ParseObject ptrn : arg0) {
//						Log.d("abeer", "Pattern =" + ptrn.toString());
//						// if (ptrn.getString("Plugin").equals(test1)) {
//						p = Pattern.compile(ptrn.getString("Pattern"));
//						m = p.matcher(test2);
//						synchronized (lock) {
//							if (m.matches()) {
//								Log.d("abeer", "does match something");
//								result = true;
//								break;
//							}
//						}
//						// }
//					}
//					// patternList.clear();
//				} else {
//					Log.d("abeer", "didnt match anything");
//					synchronized (lock) {
//						result = false;
//					}
//				}
//			}
//		});
//		return false;
	}

	

	private void watch(String url, String pluginName, String pluginService) {
		// Log.d("abeer", url+" "+pluginName+" "+pluginService) ;
		ParseObject callMonitor = new ParseObject("CallMonitor");
		int indx = url.lastIndexOf("#");
		String fragment = "";
		if (indx != -1) {
			fragment = url.substring(indx);
		} else {
			indx = url.lastIndexOf("/");
			if (indx != -1)
				fragment = url.substring(indx + 1);
			else
				fragment = url;
		}
		//View v = this.app.getRootView();
		View v = ctx.getActivity().getWindow().getDecorView().findViewById(android.R.id.content);
		v.setDrawingCacheEnabled(true);
		Bitmap capturedBitmap = Bitmap.createBitmap(v.getDrawingCache());
		v.setDrawingCacheEnabled(false);
		
		ByteArrayOutputStream stream = new ByteArrayOutputStream();
		capturedBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
		byte[] bitmapdata = stream.toByteArray();

        ParseFile imgfile = new ParseFile("photo.jpg", bitmapdata);

        imgfile.saveInBackground(new SaveCallback() {
              public void done(ParseException e) {                          
                  if (e == null) {
                     Log.d("Upload", "Success"); 
                  }else {
                     Log.e("Upload", "Failure");
                     e.printStackTrace();
                  }
                }   
               }, new ProgressCallback() {
                  public void done(Integer percentDone) {
                    // Update your progress spinner here. percentDone will be between 0 and 100.
                  }
                });
		
		
		callMonitor.put("CurrentURL", fragment);
		callMonitor.put("plugin", pluginName);
		callMonitor.put("Service", pluginService);
		callMonitor.put("ScreenShot", imgfile) ;
		
		callMonitor.saveInBackground();
	}

	/**
	 * Receives a request for execution and fulfills it by finding the
	 * appropriate Java class and calling it's execute method.
	 * 
	 * PluginManager.exec can be used either synchronously or async. In either
	 * case, a JSON encoded string is returned that will indicate if any errors
	 * have occurred when trying to find or execute the class denoted by the
	 * clazz argument.
	 * 
	 * @param service
	 *            String containing the service to run
	 * @param action
	 *            String containing the action that the class is supposed to
	 *            perform. This is passed to the plugin execute method and it is
	 *            up to the plugin developer how to deal with it.
	 * @param callbackId
	 *            String containing the id of the callback that is execute in
	 *            JavaScript if this is an async plugin call.
	 * @param rawArgs
	 *            An Array literal string containing any arguments needed in the
	 *            plugin execute method.
	 */
	// @SuppressLint("SetJavaScriptEnabled")
	public void exec(final String service, final String action,
			final String callbackId, final String rawArgs) {
		// Log.d("abeer", stage+" "+service+" "+action);
		CordovaPlugin plugin = getPlugin(service);

		if (plugin == null) {
			Log.d(TAG, "exec() call to unknown plugin: " + service);
			PluginResult cr = new PluginResult(
					PluginResult.Status.CLASS_NOT_FOUND_EXCEPTION);
			app.sendPluginResult(cr, callbackId);
			return;
		}
		CallbackContext callbackContext = new CallbackContext(callbackId, app);
		try {
			long pluginStartTime = System.currentTimeMillis();
			boolean wasValidAction;
			if (stage.equals("enforce") || stage.equals("watch")) {

				if (stage.equals("enforce")) {
					app.post(new Runnable() {

						@Override
						public void run() {
							// TODO Auto-generated method stub
							// Log.d("abeer",
							// "calling enforce "+app.getUrl()+" "+service);

							enforce(app.getUrl(), service, action);
							synchronized (lock) {
								if (!result) {
									notifyUser(app.getUrl(), service, action);
									return;
								} else {

								}
							}
						}
					});

				}
				synchronized (lock) {
					if (result) {
						wasValidAction = plugin.execute(action, rawArgs,
								callbackContext);
						if (!wasValidAction) {
							PluginResult cr = new PluginResult(
									PluginResult.Status.INVALID_ACTION);
							callbackContext.sendPluginResult(cr);
						}
					}

				}

				if (stage.equals("watch")) {
					wasValidAction = plugin.execute(action, rawArgs,
							callbackContext);
					// Log.d("abeer1", wasValidAction +" "+stage) ;
					if (wasValidAction)
						app.post(new Runnable() {

							@Override
							public void run() {
								// TODO Auto-generated method stub
								watch(app.getUrl(), service, action);
							}
						});

					// if(wasValidAction && stage.equals("watch"))
					// // Log.d("abeer", app.+ " "+service+" "+action);
					// watch(app.getUrl(), service, action);

					long duration = System.currentTimeMillis()
							- pluginStartTime;

					if (duration > SLOW_EXEC_WARNING_THRESHOLD) {
						Log.w(TAG,
								"THREAD WARNING: exec() call to "
										+ service
										+ "."
										+ action
										+ " blocked the main thread for "
										+ duration
										+ "ms. Plugin should use CordovaInterface.getThreadPool().");
					}
					if (!wasValidAction) {
						PluginResult cr = new PluginResult(
								PluginResult.Status.INVALID_ACTION);
						callbackContext.sendPluginResult(cr);
					}
				}
			}
		} catch (JSONException e) {
			PluginResult cr = new PluginResult(
					PluginResult.Status.JSON_EXCEPTION);
			callbackContext.sendPluginResult(cr);

		} catch (Exception e) {
			Log.e(TAG, "Uncaught exception from plugin", e);
			callbackContext.error(e.getMessage());
		}

	}

	@Deprecated
	public void exec(String service, String action, String callbackId,
			String jsonArgs, boolean async) {
		exec(service, action, callbackId, jsonArgs);
	}

	/**
	 * Get the plugin object that implements the service. If the plugin object
	 * does not already exist, then create it. If the service doesn't exist,
	 * then return null.
	 * 
	 * @param service
	 *            The name of the service.
	 * @return CordovaPlugin or null
	 */
	public CordovaPlugin getPlugin(String service) {
		CordovaPlugin ret = pluginMap.get(service);
		if (ret == null) {
			PluginEntry pe = entryMap.get(service);
			if (pe == null) {
				return null;
			}
			if (pe.plugin != null) {
				ret = pe.plugin;
			} else {
				ret = instantiatePlugin(pe.pluginClass);
			}
			ret.privateInitialize(ctx, app, app.getPreferences());
			pluginMap.put(service, ret);
		}
		return ret;
	}

	/**
	 * Add a plugin class that implements a service to the service entry table.
	 * This does not create the plugin object instance.
	 * 
	 * @param service
	 *            The service name
	 * @param className
	 *            The plugin class name
	 */
	public void addService(String service, String className) {
		PluginEntry entry = new PluginEntry(service, className, false);
		this.addService(entry);
	}

	/**
	 * Add a plugin class that implements a service to the service entry table.
	 * This does not create the plugin object instance.
	 * 
	 * @param entry
	 *            The plugin entry
	 */
	public void addService(PluginEntry entry) {
		this.entryMap.put(entry.service, entry);
		//Log.d("Abeerx", "Adding to entryMap key:"+entry.service);
		List<String> urlFilters = entry.getUrlFilters();
		if (urlFilters != null) {
			urlMap.put(entry.service, urlFilters);
		}
		if (entry.plugin != null) {
			entry.plugin.privateInitialize(ctx, app, app.getPreferences());
			pluginMap.put(entry.service, entry.plugin);
		}

	}

	/**
	 * Called when the system is about to start resuming a previous activity.
	 * 
	 * @param multitasking
	 *            Flag indicating if multitasking is turned on for app
	 */
	public void onPause(boolean multitasking) {
		for (CordovaPlugin plugin : this.pluginMap.values()) {
			plugin.onPause(multitasking);
		}
	}

	/**
	 * Called when the activity will start interacting with the user.
	 * 
	 * @param multitasking
	 *            Flag indicating if multitasking is turned on for app
	 */
	public void onResume(boolean multitasking) {
		for (CordovaPlugin plugin : this.pluginMap.values()) {
			plugin.onResume(multitasking);
		}
	}

	/**
	 * The final call you receive before your activity is destroyed.
	 */
	public void onDestroy() {
		for (CordovaPlugin plugin : this.pluginMap.values()) {
			plugin.onDestroy();
		}
	}

	/**
	 * Send a message to all plugins.
	 * 
	 * @param id
	 *            The message id
	 * @param data
	 *            The message data
	 * @return Object to stop propagation or null
	 */
	public Object postMessage(String id, Object data) {
		Object obj = this.ctx.onMessage(id, data);
		if (obj != null) {
			return obj;
		}
		for (CordovaPlugin plugin : this.pluginMap.values()) {
			obj = plugin.onMessage(id, data);
			if (obj != null) {
				return obj;
			}
		}
		return null;
	}

	/**
	 * Called when the activity receives a new intent.
	 */
	public void onNewIntent(Intent intent) {
		for (CordovaPlugin plugin : this.pluginMap.values()) {
			plugin.onNewIntent(intent);
		}
	}

	/**
	 * Called when the URL of the webview changes.
	 * 
	 * @param url
	 *            The URL that is being changed to.
	 * @return Return false to allow the URL to load, return true to prevent the
	 *         URL from loading.
	 */
	public boolean onOverrideUrlLoading(String url) {
		// Deprecated way to intercept URLs. (process <url-filter> tags).
		// Instead, plugins should not include <url-filter> and instead ensure
		// that they are loaded before this function is called (either by
		// setting
		// the onload <param> or by making an exec() call to them)
		for (PluginEntry entry : this.entryMap.values()) {
			List<String> urlFilters = urlMap.get(entry.service);
			if (urlFilters != null) {
				for (String s : urlFilters) {
					if (url.startsWith(s)) {
						return getPlugin(entry.service).onOverrideUrlLoading(
								url);
					}
				}
			} else {
				CordovaPlugin plugin = pluginMap.get(entry.service);
				if (plugin != null && plugin.onOverrideUrlLoading(url)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Called when the app navigates or refreshes.
	 */
	public void onReset() {
		for (CordovaPlugin plugin : this.pluginMap.values()) {
			plugin.onReset();
		}
	}

	Uri remapUri(Uri uri) {
		for (CordovaPlugin plugin : this.pluginMap.values()) {
			Uri ret = plugin.remapUri(uri);
			if (ret != null) {
				return ret;
			}
		}
		return null;
	}

	/**
	 * Create a plugin based on class name.
	 */
	private CordovaPlugin instantiatePlugin(String className) {
		CordovaPlugin ret = null;
		try {
			Class<?> c = null;
			if ((className != null) && !("".equals(className))) {
				c = Class.forName(className);
			}
			if (c != null & CordovaPlugin.class.isAssignableFrom(c)) {
				ret = (CordovaPlugin) c.newInstance();
			}
		} catch (Exception e) {
			e.printStackTrace();
			Log.d("abeer", "Error adding plugin " + className + ".");
		}
		return ret;
	}
}

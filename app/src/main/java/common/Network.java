package common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.UnknownHostException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.os.AsyncTask;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import android.util.Log;


public class Network {

	
	//
	// DownloadTaskBase
	//
	abstract public static class DownloadTaskBase extends AsyncTask<String, Long, String> {
		protected static final String TAG="DownloadTask";
		protected String errorMessage;
		protected int httpCode;
		
		// Functions to be implemented by derived classes
		abstract protected HttpURLConnection getHttpRequest() throws IOException;
		abstract protected void onError(String errorText);
		
		protected boolean processStatusLine(HttpURLConnection response) {
			try {
				httpCode = response.getResponseCode();
				Log.d(TAG, "got HTTP " + httpCode);
			} catch (IOException e) {
				errorMessage = e.getMessage();
				httpCode = 500;
				Log.e(TAG, "Exception", e);
			}
			if (httpCode != 200 && httpCode !=206) {
				if (httpCode == 403) {
					errorMessage = "Access Denied"; // urgh! can we be less forbidding?
				}
				else {
					errorMessage = "HTTP Error " + httpCode;
				}
				return false;
			}
			return true;
		}
		
	}
	
	//
	// DownloadTextTask - a background task for downloading relatively small
	//                    amounts of text.
	//
	abstract public static class DownloadTextTask extends DownloadTaskBase {

		protected String responseETag = "";
		
		// Functions to be implemented by derived classes
		abstract protected void onDownloadComplete(String str);

		@Override
		protected String doInBackground(String... params) {				
			try {
				HttpURLConnection request = getHttpRequest();
				Log.d(TAG, request.getURL().toString());
				if (!processStatusLine(request)) {
					return null;
				}
				responseETag = request.getHeaderField("ETag");
				if (responseETag == null) {
					responseETag = "";
				}
				String rv = Utils.getHttpResponseText(request);
				Log.d("DownloadTextTask", "Response text is:\n" + rv);
				return rv;
			}
			catch (UnknownHostException e) {
				errorMessage = "No network";
			}
			catch (IOException e) {
				errorMessage = "Error during download: " + e.getMessage();
			}				
			return null;
		}

		@Override protected void onPostExecute(String str) {
			if (str == null) {
				if (TextUtils.isEmpty(errorMessage)) {
					errorMessage = "Unspecified error"; 
				}
				Log.e(TAG, errorMessage);
				onError(errorMessage);
				return;
			}
			onDownloadComplete(str);
		}
	}
	


	//
	// DownloadJsonTask - helper for downloading JSON
	//
	abstract public static class DownloadJsonTask extends DownloadTextTask {
		
		// Functions to be implemented by derived classes
		abstract protected void onDownloadJsonComplete(Object object) throws JSONException;
		
		// Parse the text response into JSON
		@Override
		protected final void onDownloadComplete(String str) {
			JSONTokener tokener = new JSONTokener(str);
			try {
				Object object = tokener.nextValue();
				if (object instanceof JSONObject) {
					onDownloadJsonComplete(object);
				}
				else if (object instanceof JSONArray) {
					onDownloadJsonComplete(object);
				}
				else {
					onError("JSON response is an unexpected data type");
				}
			} catch (JSONException e) {
				onError("JSONException while parsing response: " + e.getLocalizedMessage());
			}
		}
	}



	//
	// DownloadBinaryTask - a background task for downloading any size any kind of data
	//
	abstract public static class DownloadBinaryTask extends DownloadTaskBase {
		protected String localPath;
		protected long cbTotalSize = -1;
		protected long cbDownloaded = 0;
		protected boolean append;
		public String contentType;
		
		// Functions to be implemented by derived classes
		abstract protected void onDownloadComplete();

		// Constructor.
		public DownloadBinaryTask(String localPath, boolean append) {
			this.localPath = localPath;
			this.append = append;
		}
		
		@Override
		protected String doInBackground(String... params) {
			File outputFile=new File(localPath);
			try {
				HttpURLConnection request = getHttpRequest();
				long cbDownloaded = 0;
				if (outputFile.exists()) {
					if (!append) {
						outputFile.delete();
					}
					else {
						cbDownloaded = outputFile.length();
						Log.d(TAG, "Adding header: Range: " + cbDownloaded + "-");
						request.addRequestProperty("Range", "bytes=" + cbDownloaded + "-");
					}
				}
				request.connect();
				if (!processStatusLine(request)) {
					return null;
				}
				contentType = "";
				String hdrContentType = request.getContentType();
				if (hdrContentType != null) {
					contentType = hdrContentType;
				}
				try (InputStream inputStream = request.getInputStream()) {
					byte[] sBuffer = new byte[16384];
					long cbThisDownloadSize  = request.getContentLength();
					cbTotalSize = cbDownloaded + cbThisDownloadSize;
					Log.d(TAG, "cbTotalSize is " + cbTotalSize);
			    	try (RandomAccessFile output = new RandomAccessFile(localPath, "rw")) {
						if (cbDownloaded > 0) {
							output.seek(cbDownloaded);
						}
						int readBytes = 0;
						while ((readBytes = inputStream.read(sBuffer)) != -1) {
							output.write(sBuffer, 0, readBytes);
							cbDownloaded += readBytes;
							publishProgress(cbDownloaded, cbTotalSize);
							if (isCancelled()) {
								break;
							}
						}
					}
				    return "OK"; // success
				}
			}
			catch (IOException e2) {
				errorMessage = "Exception in download: " + e2.getLocalizedMessage();
			}				
			return null;
		}
		
		@Override protected void onPostExecute(String str) {
			if (isCancelled()) {
				return;
			}
			if (str == null) {
				if (TextUtils.isEmpty(errorMessage)) {
					errorMessage = "Unspecified error"; 
				}
				Log.e(TAG, errorMessage);
				onError(errorMessage);
				return;
			}
			onDownloadComplete();
		}
	}

}

package com.fanfou.app.service;

import java.io.File;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.fanfou.app.App;
import com.fanfou.app.DraftsPage;
import com.fanfou.app.R;
import com.fanfou.app.WritePage;
import com.fanfou.app.api.Api;
import com.fanfou.app.api.ApiException;
import com.fanfou.app.api.Draft;
import com.fanfou.app.api.Status;
import com.fanfou.app.config.Actions;
import com.fanfou.app.config.Commons;
import com.fanfou.app.db.Contents.DraftInfo;
import com.fanfou.app.http.ApnType;
import com.fanfou.app.util.ImageHelper;
import com.fanfou.app.util.OptionHelper;
import com.fanfou.app.util.StringHelper;

/**
 * @author mcxiaoke
 * @version 1.0 2011.06.10
 * @version 1.1 2011.10.25
 * @version 2.0 2011.10.27
 * @version 2.1 2011.10.28
 * @version 2.2 2011.11.02
 * 
 */
public class PostStatusService extends WakefulIntentService {

	private static final String TAG = PostStatusService.class.getSimpleName();
	private NotificationManager nm;

	public void log(String message) {
		Log.d(TAG, message);
	}

	private String text;
	private File srcFile;
	private String location;
	private String relationId;
	private int type;

	public PostStatusService() {
		super("UpdateService");

	}

	@Override
	protected void onHandleIntent(Intent intent) {
		if (intent == null) {
			return;
		}
		log("intent=" + intent);
		this.nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		parseIntent(intent);
		if (doSend()) {
			sendSuccessBroadcast();
		}
	}

	private void parseIntent(Intent intent) {
		type = intent.getIntExtra(Commons.EXTRA_TYPE, WritePage.TYPE_NORMAL);
		text = intent.getStringExtra(Commons.EXTRA_TEXT);
		srcFile = (File) intent.getSerializableExtra(Commons.EXTRA_FILE);
		relationId = intent.getStringExtra(Commons.EXTRA_IN_REPLY_TO_ID);
		location = intent.getStringExtra(Commons.EXTRA_LOCATION);
		if (App.DEBUG) {
			log("location="
					+ (StringHelper.isEmpty(location) ? "null" : location));
		}
	}

	private boolean doSend() {
		showSendingNotification();
		boolean res = false;
		Api api = App.me.api;
		try {
			Status result = null;
			if (type == WritePage.TYPE_REPLY) {
				result = api.statusUpdate(text, relationId, null, location,
						null);
			} else {
				if (srcFile == null || !srcFile.exists()) {
					result = api.statusUpdate(text, null, null, location,
							relationId);
				} else {
					int quality = ImageHelper.IMAGE_QUALITY_MEDIUM;
					if (App.me.apnType == ApnType.WIFI
							|| App.me.apnType == ApnType.HSDPA) {
						quality = ImageHelper.IMAGE_QUALITY_HIGH;
					} else {
						quality = OptionHelper
								.parseInt(
										this,
										R.string.option_photo_quality,
										String.valueOf(ImageHelper.IMAGE_QUALITY_MEDIUM));
					}
					File photo = ImageHelper.prepareUploadFile(this, srcFile,
							quality);
					if (photo != null && photo.length() > 0) {
						if (App.DEBUG)
							log("photo file=" + srcFile.getName() + " size="
									+ photo.length() / 1024 + " quality="
									+ quality);
						result = api.photoUpload(photo, text, null, location);
					}
					photo.delete();
				}
			}
			nm.cancel(0);
			if (result != null && !result.isNull()) {
				res = true;
			}
		} catch (ApiException e) {
			if (App.DEBUG) {
				Log.e(TAG,
						"error: code=" + e.statusCode + " msg="
								+ e.getMessage());
				e.printStackTrace();
			}
			showFailedNotification("消息未发送，已保存到草稿箱",
					getString(R.string.connection_error_msg));
		} finally {
			nm.cancel(0);
		}
		return res;
	}

	private int showSendingNotification() {
		int id = 0;
		Notification notification = new Notification(R.drawable.ic_notify_icon,
				"饭否消息正在发送...", System.currentTimeMillis());
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(), 0);
		notification.setLatestEventInfo(this, "饭否消息", "正在发送...", contentIntent);
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		nm.notify(id, notification);
		return id;
	}

	private int showFailedNotification(String title, String message) {
		doSaveDrafts();
		int id = 1;
		Notification notification = new Notification(R.drawable.ic_notify_icon,
				title, System.currentTimeMillis());
		Intent intent = new Intent(this, DraftsPage.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				intent, PendingIntent.FLAG_UPDATE_CURRENT);
		notification.setLatestEventInfo(this, title, message, contentIntent);
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
		nm.notify(id, notification);
		return id;
	}

	private void doSaveDrafts() {
		Draft d = new Draft();
		d.text = text;
		d.filePath = srcFile == null ? "" : srcFile.getPath();
		d.replyTo = relationId;
		d.type = type;
		Uri resultUri = getContentResolver().insert(DraftInfo.CONTENT_URI,
				d.toContentValues());
		if (App.DEBUG) {
			log("doSaveDrafts resultUri=" + resultUri + " type=" + d.type
					+ " text=" + d.text + " filepath=" + d.filePath);
		}
	}

	private void sendSuccessBroadcast() {
		Intent intent = new Intent(Actions.ACTION_STATUS_SEND);
		intent.setPackage(getPackageName());
		sendOrderedBroadcast(intent, null);
	}

}

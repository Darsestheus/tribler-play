package org.tribler.tsap.streaming;

import org.tribler.tsap.Torrent;
import org.tribler.tsap.downloads.Download;
import org.tribler.tsap.downloads.DownloadStatus;
import org.tribler.tsap.downloads.XMLRPCDownloadManager;
import org.tribler.tsap.util.MainThreadPoller;
import org.tribler.tsap.util.Poller.IPollListener;
import org.tribler.tsap.util.Utility;
import org.videolan.vlc.gui.video.VideoPlayerActivity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

/**
 * Class that makes sure that a torrent is downloaded (if needed), shows a
 * dialog with the download status and starts VLC when the video is ready to be
 * streamed.
 * 
 * @author Niels Spruit
 * 
 */
public class PlayButtonListener implements OnClickListener, IPollListener {

	private final int POLLER_INTERVAL = 1000; // in ms

	private Torrent thumbData;
	private String infoHash;
	private boolean needsToBeDownloaded;
	private MainThreadPoller mPoller;
	private VODDialogFragment dialog;
	private boolean inVODMode = false;
	private Activity mActivity;

	public PlayButtonListener(Torrent thumbData, Activity activity) {
		this.thumbData = thumbData;
		this.infoHash = thumbData.getInfoHash();
		this.needsToBeDownloaded = true;
		this.mActivity = activity;
		this.mPoller = new MainThreadPoller(this, POLLER_INTERVAL, mActivity);
	}

	public PlayButtonListener(String infoHash, Activity activity) {
		this.thumbData = null;
		this.infoHash = infoHash;
		this.needsToBeDownloaded = false;
		this.mActivity = activity;
		this.mPoller = new MainThreadPoller(this, POLLER_INTERVAL, mActivity);
	}

	@Override
	public void onClick(View buttonClicked) {
		// start downloading the torrent
		Button button = (Button) buttonClicked;
		if (needsToBeDownloaded) {
			startDownload();
		}

		// disable the play button
		button.setEnabled(false);

		// start waiting for VOD
		mPoller.start();
		dialog = new VODDialogFragment(mPoller, button);
		dialog.show(mActivity.getFragmentManager(), "wait_vod");
	}

	/**
	 * Starts downloading the torrent
	 */
	private void startDownload() {
		XMLRPCDownloadManager.getInstance().downloadTorrent(infoHash,
				thumbData.getName());
	}

	@Override
	public void onPoll() {
		XMLRPCDownloadManager.getInstance().getProgressInfo(infoHash);
		Download dwnld = XMLRPCDownloadManager.getInstance()
				.getCurrentDownload();
		AlertDialog aDialog = (AlertDialog) dialog.getDialog();
		if (dwnld != null) {
			if (!dwnld.getTorrent().getInfoHash().equals(infoHash)) {
				return;
			}

			if (dwnld.isVODPlayable()) {
				Intent intent = new Intent(Intent.ACTION_VIEW,
						XMLRPCDownloadManager.getInstance().getVideoUri(),
						mActivity.getApplicationContext(),
						VideoPlayerActivity.class);
				mActivity.startActivity(intent);
				mPoller.stop();
				aDialog.cancel();

			} else {
				DownloadStatus downStat = dwnld.getDownloadStatus();
				int statusCode = downStat.getStatus();

				switch (statusCode) {
				case 3:
					// if state is downloading, start vod mode if not done
					// already:
					if (!inVODMode) {
						XMLRPCDownloadManager.getInstance().startVOD(infoHash);
						inVODMode = true;
					}

					Double vod_eta = dwnld.getVOD_ETA();
					Log.d("PlayButtonListener", "VOD_ETA is: " + vod_eta);

					if (aDialog != null)
						aDialog.setMessage("Video starts playing in about "
								+ Utility.convertSecondsToString(vod_eta)
								+ " ("
								+ Utility.convertBytesPerSecToString(downStat
										.getDownloadSpeed()) + ").");

					break;
				default:
					if (aDialog != null) {
						aDialog.setMessage("Download status: "
								+ Utility
										.convertDownloadStateIntToMessage(statusCode)
								+ ((statusCode == 2) ? " ("
										+ Math.round(downStat.getProgress() * 100)
										+ "%)"
										: ""));
					}
					break;
				}

			}
		}
	}
}

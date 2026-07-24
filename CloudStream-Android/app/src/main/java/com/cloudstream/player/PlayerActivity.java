package com.cloudstream.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class PlayerActivity extends Activity {
    public static MainActivity.TrackItem track;
    public static MediaPlayer player;
    public static android.media.audiofx.Equalizer equalizer;
    private boolean isShuffle = false;
    private boolean isRepeat = false;
    private LocalProxyServer proxyServer;
    private SeekBar seekBar;
    private TextView currentTimeTv, totalTimeTv, titleView, artistView;
    private ImageView playPauseBtn;
    private ImageView artworkView;
    private Handler handler = new Handler();
    public static boolean isPrepared = false;
    private CircularProgressView progressView;
    private boolean isDark;

    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                if (player != null && isPrepared && player.isPlaying()) {
                    int pos = player.getCurrentPosition();
                    seekBar.setProgress(pos);
                    currentTimeTv.setText(formatTime(pos));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            handler.postDelayed(this, 250);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.content.SharedPreferences themePrefs = getSharedPreferences("Settings", android.content.Context.MODE_PRIVATE);
        isDark = themePrefs.getBoolean("Dark Theme", true); // Sync fix

        if (proxyServer == null) {
            proxyServer = new LocalProxyServer();
            proxyServer.start();
        }
        MainActivity.TrackItem oldTrack = track;
        track = (MainActivity.TrackItem) getIntent().getSerializableExtra("track");
        if (track == null) { finish(); return; }
        saveTrack(track);
        if (oldTrack == null || !oldTrack.id.equals(track.id)) {
            if (player != null) {
                try { if (equalizer != null) { equalizer.release(); equalizer = null; } player.release(); } catch(Exception e) {}
                player = null;
            }
        }

        android.widget.FrameLayout frameRoot = new android.widget.FrameLayout(this);
        frameRoot.setBackgroundColor((isDark ? Color.parseColor("#0A0A0A") : Color.parseColor("#F5F5F5")));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(16, 16, 16, 16);

        // Header
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        ImageView backBtn = new ImageView(this);
        backBtn.setImageResource(android.R.drawable.ic_menu_revert);
        backBtn.setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")));
        backBtn.setPadding(4, 4, 4, 4);
        backBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { onBackPressed(); }
        });
        headerRow.addView(backBtn);

        TextView space = new TextView(this);
        headerRow.addView(space, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        ImageView menuBtn = new ImageView(this);
        menuBtn.setImageResource(android.R.drawable.ic_menu_more);
        menuBtn.setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")));
        menuBtn.setPadding(4, 4, 4, 4);
        menuBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(PlayerActivity.this);
                builder.setTitle("Options");
                String[] opts = {"Share", "Sleep Timer", "Song Info"};
                builder.setItems(opts, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            android.content.Intent sendIntent = new android.content.Intent();
                            sendIntent.setAction(android.content.Intent.ACTION_SEND);
                            sendIntent.putExtra(android.content.Intent.EXTRA_TEXT, "Listen to " + track.title + " by " + track.artist + " on BerryMusic!");
                            sendIntent.setType("text/plain");
                            startActivity(android.content.Intent.createChooser(sendIntent, "Share Track"));
                        } else {
                            Toast.makeText(PlayerActivity.this, opts[which] + " selected", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                builder.show();
            }
        });
        headerRow.addView(menuBtn);

        root.addView(headerRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Artwork (Weight 1 so it takes available space instead of scrolling)
        artworkView = new ImageView(this);
        artworkView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams artParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f);
        artParams.gravity = Gravity.CENTER_HORIZONTAL;
        artParams.setMargins(16, 16, 16, 16);
        root.addView(artworkView, artParams);

        // Info Row
        LinearLayout infoRow = new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);

        final ImageView heartBtn = new ImageView(this);
        heartBtn.setTag("heartBtn");
        heartBtn.setPadding(4, 4, 4, 4);
        final Runnable updateFavBtn = new Runnable() {
            @Override public void run() {
                if (MainActivity.isInList(PlayerActivity.this, "favorites", track)) {
                    heartBtn.setImageResource(android.R.drawable.btn_star_big_on);
                    heartBtn.setColorFilter(Color.parseColor("#FFD700"));
                } else {
                    heartBtn.setImageResource(android.R.drawable.btn_star_big_off);
                    heartBtn.setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")));
                }
            }
        };
        updateFavBtn.run();

        heartBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (MainActivity.isInList(PlayerActivity.this, "favorites", track)) {
                    MainActivity.removeFromList(PlayerActivity.this, "favorites", track);
                    Toast.makeText(PlayerActivity.this, "Removed from favorites", Toast.LENGTH_SHORT).show();
                } else {
                    MainActivity.saveToList(PlayerActivity.this, "favorites", track);
                    Toast.makeText(PlayerActivity.this, "Added to favorites", Toast.LENGTH_SHORT).show();
                }
                updateFavBtn.run();
            }
        });
        infoRow.addView(heartBtn);

        LinearLayout textLayout = new LinearLayout(this);
        textLayout.setOrientation(LinearLayout.VERTICAL);
        textLayout.setGravity(Gravity.CENTER);

        titleView = new TextView(this);
        titleView.setText(track.title);
        titleView.setTextColor((isDark ? Color.WHITE : Color.parseColor("#121212")));
        titleView.setTextSize(18); titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setTypeface(null, android.graphics.Typeface.BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER);
        textLayout.addView(titleView);

        artistView = new TextView(this);
        artistView.setText(track.artist);
        artistView.setTextColor((isDark ? Color.parseColor("#BBBBBB") : Color.parseColor("#666666")));
        artistView.setTextSize(12);
        artistView.setGravity(Gravity.CENTER);
        artistView.setSingleLine(true);
        artistView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textLayout.addView(artistView);

        infoRow.addView(textLayout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        ImageView queueBtn = new ImageView(this);
        queueBtn.setImageResource(android.R.drawable.ic_menu_agenda);
        queueBtn.setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")));
        queueBtn.setPadding(4, 4, 4, 4);
        queueBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                MainActivity.saveToList(PlayerActivity.this, "queue", track);
                Toast.makeText(PlayerActivity.this, "Added to Queue", Toast.LENGTH_SHORT).show();
            }
        });
        infoRow.addView(queueBtn);

        root.addView(infoRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Progress row (SeekBar)
        LinearLayout progressLayout = new LinearLayout(this);
        progressLayout.setOrientation(LinearLayout.HORIZONTAL);
        progressLayout.setGravity(Gravity.CENTER_VERTICAL);

        currentTimeTv = new TextView(this);
        currentTimeTv.setTextColor((isDark ? Color.parseColor("#888888") : Color.parseColor("#666666")));
        currentTimeTv.setText("0:00");
        currentTimeTv.setTextSize(10);
        progressLayout.addView(currentTimeTv);

        seekBar = new SeekBar(this);
        if (android.os.Build.VERSION.SDK_INT >= 16) {
            seekBar.getProgressDrawable().setColorFilter(Color.parseColor("#9D4EDD"), android.graphics.PorterDuff.Mode.SRC_IN);
            seekBar.getThumb().setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")), android.graphics.PorterDuff.Mode.SRC_IN);
        }
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        seekParams.setMargins(8, 0, 8, 0);
        progressLayout.addView(seekBar, seekParams);

        totalTimeTv = new TextView(this);
        totalTimeTv.setTextColor((isDark ? Color.parseColor("#888888") : Color.parseColor("#666666")));
        totalTimeTv.setText(formatTime(track.duration));
        totalTimeTv.setTextSize(10);
        progressLayout.addView(totalTimeTv);

        LinearLayout.LayoutParams pRowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pRowParams.setMargins(0, 16, 0, 16);
        root.addView(progressLayout, pRowParams);

        // Volume Control Row
        LinearLayout volLayout = new LinearLayout(this);
        volLayout.setOrientation(LinearLayout.HORIZONTAL);
        volLayout.setGravity(Gravity.CENTER_VERTICAL);
        volLayout.setPadding(32, 0, 32, 16);

        ImageView volDownBtn = new ImageView(this);
        volDownBtn.setImageResource(android.R.drawable.ic_media_rew); // Placeholder volume down
        volDownBtn.setColorFilter((isDark ? Color.parseColor("#888888") : Color.parseColor("#666666")));
        volLayout.addView(volDownBtn);

        final android.media.AudioManager audioManager = (android.media.AudioManager) getSystemService(android.content.Context.AUDIO_SERVICE);
        SeekBar volBar = new SeekBar(this);
        int maxVol = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC);
        int curVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC);
        volBar.setMax(maxVol);
        volBar.setProgress(curVol);
        if (android.os.Build.VERSION.SDK_INT >= 16) {
            volBar.getProgressDrawable().setColorFilter(Color.parseColor("#9D4EDD"), android.graphics.PorterDuff.Mode.SRC_IN);
            volBar.getThumb().setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")), android.graphics.PorterDuff.Mode.SRC_IN);
        }

        volBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, progress, 0);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        LinearLayout.LayoutParams vParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        vParams.setMargins(16, 0, 16, 0);
        volLayout.addView(volBar, vParams);

        ImageView volUpBtn = new ImageView(this);
        volUpBtn.setImageResource(android.R.drawable.ic_media_ff); // Placeholder volume up
        volUpBtn.setColorFilter((isDark ? Color.parseColor("#888888") : Color.parseColor("#666666")));
        volLayout.addView(volUpBtn);

        root.addView(volLayout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Play Controls
        LinearLayout playControls = new LinearLayout(this);
        playControls.setOrientation(LinearLayout.HORIZONTAL);
        playControls.setGravity(Gravity.CENTER);

        ImageView shuffleBtn = new ImageView(this);
        shuffleBtn.setImageResource(android.R.drawable.ic_menu_sort_by_size);
        shuffleBtn.setPadding(4, 4, 4, 4);
        shuffleBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            isShuffle = !isShuffle;
            shuffleBtn.setColorFilter(isShuffle ? Color.parseColor("#00E5FF") : (isDark ? Color.WHITE : Color.parseColor("#121212")));
            Toast.makeText(PlayerActivity.this, isShuffle ? "Shuffle enabled" : "Shuffle disabled", Toast.LENGTH_SHORT).show();
        } });
        playControls.addView(shuffleBtn);

        ImageView prevBtn = new ImageView(this);
        LinearLayout.LayoutParams prevNextParams = new LinearLayout.LayoutParams(
                (int)(getResources().getDisplayMetrics().density * 32),
                (int)(getResources().getDisplayMetrics().density * 32));
        prevBtn.setImageResource(android.R.drawable.ic_media_previous);
        prevBtn.setPadding(4, 4, 4, 4);
        prevBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            int idx = -1;
            if (MainActivity.tracks != null) {
                for (int i=0; i<MainActivity.tracks.size(); i++) {
                    if (MainActivity.tracks.get(i).id.equals(track.id)) {
                        idx = i;
                        break;
                    }
                }
            }
            if (idx > 0) {
                track = MainActivity.tracks.get(idx - 1);
                if(player != null) { player.release(); player = null; }
                titleView.setText(track.title);
                artistView.setText(track.artist);
                loadArtwork();
                initPlayer();
            } else {
                Toast.makeText(PlayerActivity.this, "No previous track", Toast.LENGTH_SHORT).show();
            }
        } });
        playControls.addView(prevBtn, prevNextParams);

        playPauseBtn = new ImageView(this);
        playPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
        playPauseBtn.setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")));
        playPauseBtn.setScaleType(ImageView.ScaleType.CENTER);
        playPauseBtn.setPadding(0, 0, 0, 0);
        android.graphics.drawable.GradientDrawable btnBg = new android.graphics.drawable.GradientDrawable();
        btnBg.setColor(Color.parseColor("#9D4EDD")); // Purple circle
        btnBg.setShape(android.graphics.drawable.GradientDrawable.OVAL);

        int btnSize = (int)(getResources().getDisplayMetrics().density * 32);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(btnSize, btnSize);
        playParams.setMargins(24, 0, 24, 0);

        if (android.os.Build.VERSION.SDK_INT >= 16) {
            playPauseBtn.setBackground(btnBg);
        } else {
            playPauseBtn.setBackgroundDrawable(btnBg);
        }
        playPauseBtn.setEnabled(false);
        playControls.addView(playPauseBtn, playParams);

        ImageView nextBtn = new ImageView(this);
        nextBtn.setImageResource(android.R.drawable.ic_media_next);
        nextBtn.setPadding(4, 4, 4, 4);
        nextBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            int idx = -1;
            if (MainActivity.tracks != null) {
                for (int i=0; i<MainActivity.tracks.size(); i++) {
                    if (MainActivity.tracks.get(i).id.equals(track.id)) {
                        idx = i;
                        break;
                    }
                }
            }
            if (idx != -1 && idx < MainActivity.tracks.size() - 1) {
                track = MainActivity.tracks.get(idx + 1);
                if(player != null) { player.release(); player = null; }
                titleView.setText(track.title);
                artistView.setText(track.artist);
                loadArtwork();
                initPlayer();
            } else {
                Toast.makeText(PlayerActivity.this, "No next track", Toast.LENGTH_SHORT).show();
            }
        } });
        playControls.addView(nextBtn, prevNextParams);

        ImageView repeatBtn = new ImageView(this);
        repeatBtn.setImageResource(android.R.drawable.ic_menu_revert);
        repeatBtn.setPadding(4, 4, 4, 4);
        repeatBtn.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            isRepeat = !isRepeat;
            repeatBtn.setColorFilter(isRepeat ? Color.parseColor("#00E5FF") : (isDark ? Color.WHITE : Color.parseColor("#121212")));
            Toast.makeText(PlayerActivity.this, isRepeat ? "Repeat enabled" : "Repeat disabled", Toast.LENGTH_SHORT).show();
        } });
        playControls.addView(repeatBtn);

        root.addView(playControls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Bottom Actions
        LinearLayout bottomActions = new LinearLayout(this);
        bottomActions.setOrientation(LinearLayout.HORIZONTAL);
        bottomActions.setPadding(0, 16, 0, 8);

        int[] actionIcons = {android.R.drawable.ic_menu_sort_by_size, android.R.drawable.ic_menu_add, android.R.drawable.ic_menu_preferences};
        String[] actionLabels = {"Lyrics", "Add to Playlist", "Equalizer"};

        for (int i=0; i<3; i++) {
            LinearLayout actionItem = new LinearLayout(this);
            actionItem.setOrientation(LinearLayout.VERTICAL);
            actionItem.setGravity(Gravity.CENTER);

            final int idx = i;
            actionItem.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {

                    if (actionLabels[idx].equals("Lyrics")) {
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(PlayerActivity.this);
                        builder.setTitle("Lyrics");
                        builder.setMessage("Lyrics are not available for this track yet.\n\nEnjoy the music!");
                        builder.setPositiveButton("OK", null);
                        builder.show();
                    } else if (actionLabels[idx].equals("Add to Playlist")) {
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(PlayerActivity.this);
                        builder.setTitle("Add to Playlist");
                        String[] playlists = {"Favorites", "Chill Vibes", "Workout", "Create New..."};
                        builder.setItems(playlists, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                MainActivity.saveToList(PlayerActivity.this, "library", track);
                                Toast.makeText(PlayerActivity.this, "Added to " + playlists[which], Toast.LENGTH_SHORT).show();
                            }
                        });
                        builder.show();
                    } else if (actionLabels[idx].equals("Equalizer")) {
                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(PlayerActivity.this);
                        builder.setTitle("Equalizer");
                        final String[] presets = {"Normal", "Classical", "Dance", "Flat", "Folk", "Heavy Metal", "Hip Hop", "Jazz", "Pop", "Rock"};
                        builder.setItems(presets, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                if (player != null && equalizer != null) {
                                    try {
                                        short numPresets = equalizer.getNumberOfPresets();
                                        for (short i = 0; i < numPresets; i++) {
                                            if (equalizer.getPresetName(i).equalsIgnoreCase(presets[which])) {
                                                equalizer.usePreset(i);
                                                Toast.makeText(PlayerActivity.this, "Equalizer set to " + presets[which], Toast.LENGTH_SHORT).show();
                                                return;
                                            }
                                        }
                                        Toast.makeText(PlayerActivity.this, "Preset not supported by device", Toast.LENGTH_SHORT).show();
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        Toast.makeText(PlayerActivity.this, "Equalizer error", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Toast.makeText(PlayerActivity.this, "Player not initialized", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        builder.show();
                    }

                }
            });

            ImageView icon = new ImageView(this);
            icon.setImageResource(actionIcons[i]);
            icon.setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")));
            icon.setPadding(0, 0, 0, 0);
            int iconSize = (int)(getResources().getDisplayMetrics().density * 16);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.setMargins(0, 4, 0, 4);
            actionItem.addView(icon, iconParams);

            TextView label = new TextView(this);
            label.setText(actionLabels[i]);
            label.setTextColor((isDark ? Color.WHITE : Color.parseColor("#121212")));
            label.setTextSize(10);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, 4, 0, 0);
            actionItem.addView(label);

            bottomActions.addView(actionItem, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
        }
        root.addView(bottomActions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(android.R.drawable.arrow_up_float);
        chevron.setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")));
        chevron.setPadding(0, 8, 0, 8);
        root.addView(chevron, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        frameRoot.addView(root, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progressView = new CircularProgressView(this);
        android.widget.FrameLayout.LayoutParams progParams = new android.widget.FrameLayout.LayoutParams(80, 80);
        progParams.gravity = Gravity.CENTER;
        progressView.setLayoutParams(progParams);
        progressView.setVisibility(View.GONE);
        frameRoot.addView(progressView, progParams);

        setContentView(frameRoot);

        loadArtwork();
        initPlayer();

        playPauseBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayPause();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && isPrepared && player != null) {
                    player.seekTo(progress);
                    currentTimeTv.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }


    private void saveTrack(MainActivity.TrackItem t) {
        if (t == null) return;
        android.content.SharedPreferences prefs = getSharedPreferences("CloudStream", MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();
        editor.putString("track_id", t.id);
        editor.putString("track_title", t.title);
        editor.putString("track_artist", t.artist);
        editor.putString("track_artworkUrl", t.artworkUrl);
        editor.putString("track_transcodingsUrl", t.transcodingsUrl);
        editor.putInt("track_duration", t.duration);
        editor.apply();
    }

    private void loadArtwork() {
        if(titleView != null) {
            final ImageView hBtn = (ImageView) titleView.getRootView().findViewWithTag("heartBtn");
            if (hBtn != null) {
                if (MainActivity.isInList(PlayerActivity.this, "favorites", track)) {
                    hBtn.setImageResource(android.R.drawable.btn_star_big_on);
                    hBtn.setColorFilter(Color.parseColor("#FFD700"));
                } else {
                    hBtn.setImageResource(android.R.drawable.btn_star_big_off);
                    hBtn.setColorFilter((isDark ? Color.WHITE : Color.parseColor("#121212")));
                }
            }
        }
        String artUrl = track.artworkUrl;
        if (artUrl != null && !artUrl.isEmpty()) {
            artUrl = artUrl.replace("large.jpg", "t500x500.jpg");
            SoundCloudAPI.getTrackArtwork(artUrl, handler, new SoundCloudAPI.ArtworkCallback() {
                @Override
                public void onSuccess(Bitmap bitmap) {
                    if (bitmap != null) {
                        Bitmap rounded = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(rounded);
                        Paint paint = new Paint();
                        paint.setAntiAlias(true);
                        paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                        canvas.drawRoundRect(new android.graphics.RectF(0, 0, bitmap.getWidth(), bitmap.getHeight()), 32, 32, paint);
                        artworkView.setImageBitmap(rounded);
                        artworkView.setBackgroundColor(Color.TRANSPARENT);
                    }
                }
                @Override public void onError(String error) {}
            });
        }
    }

    private void initPlayer() {
        if (progressView != null) { progressView.setVisibility(View.VISIBLE); progressView.startAnimation(); }
        if (player != null && isPrepared) {
            if (progressView != null) { progressView.stopAnimation(); progressView.setVisibility(View.GONE); }
            seekBar.setMax(player.getDuration());
            totalTimeTv.setText(formatTime(player.getDuration()));
            playPauseBtn.setEnabled(true);
            playPauseBtn.setImageResource(player.isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
            handler.post(updateRunnable);
            return;
        } else if (player != null && !isPrepared) {
            // Already preparing
            return;
        }

        final String currentInitId = track.id;

        if (track.transcodingsUrl == null || track.transcodingsUrl.equals("null") || track.transcodingsUrl.isEmpty()) {
            progressView.stopAnimation();
            progressView.setVisibility(View.GONE);
            Toast.makeText(this, "Stream unavailable", Toast.LENGTH_SHORT).show();
            return;
        }

        SoundCloudAPI.getStreamUrl(track.transcodingsUrl, handler, new SoundCloudAPI.StreamUrlCallback() {
            @Override
            public void onSuccess(String url) {
                if (track == null || !track.id.equals(currentInitId)) return;
                try {
                    if (player != null) {
                        try { if (equalizer != null) { equalizer.release(); equalizer = null; } player.release(); } catch(Exception e){}
                        player = null;
                    }
                    player = new MediaPlayer();
                    player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    try {
                        if (equalizer != null) { equalizer.release(); equalizer = null; }
                        equalizer = new android.media.audiofx.Equalizer(0, player.getAudioSessionId());
                        equalizer.setEnabled(true);
                    } catch(Exception e) { e.printStackTrace(); }
                    player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override
                        public boolean onError(MediaPlayer mp, int what, int extra) {
                            return true;
                        }
                    });
                    String streamUrl = url;
                    if (streamUrl != null && streamUrl.startsWith("https://") && proxyServer != null) {
                        try {
                            String encodedUrl = java.net.URLEncoder.encode(streamUrl, "UTF-8");
                            streamUrl = "http://127.0.0.1:" + proxyServer.getPort() + "/?url=" + encodedUrl;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    player.setDataSource(streamUrl);
                    player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            isPrepared = true;
                            progressView.stopAnimation();
                            progressView.setVisibility(View.GONE);
                            seekBar.setMax(mp.getDuration());
                            totalTimeTv.setText(formatTime(mp.getDuration()));
                            playPauseBtn.setEnabled(true);
                            playPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
                            mp.start();
                            handler.post(updateRunnable);

                            // Start foreground service for keeping app alive
                            Intent serviceIntent = new Intent(PlayerActivity.this, MusicService.class);
                            serviceIntent.putExtra("track_title", track.title);
                            serviceIntent.putExtra("track_artist", track.artist);
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= 26) {
                                    startForegroundService(serviceIntent);
                                } else {
                                    startService(serviceIntent);
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    });
                    player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                        @Override
                        public boolean onError(MediaPlayer mp, int what, int extra) {
                            Toast.makeText(PlayerActivity.this, "Playback Error: " + what + " " + extra, Toast.LENGTH_LONG).show();
                            return false;
                        }
                    });

                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            if (isRepeat) {
                                mp.seekTo(0);
                                mp.start();
                            } else {
                                playNextTrack();
                            }
                        }
                    });
                    player.prepareAsync();
                } catch (Exception e) {
                    Toast.makeText(PlayerActivity.this, "Error initializing player", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String error) {
                if (track == null || !track.id.equals(currentInitId)) return;
                progressView.stopAnimation();
                progressView.setVisibility(View.GONE);
                Toast.makeText(PlayerActivity.this, "Failed to get stream: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }


    private void playNextTrack() {
        int idx = -1;
        if (MainActivity.tracks != null) {
            for (int i=0; i<MainActivity.tracks.size(); i++) {
                if (MainActivity.tracks.get(i).id.equals(track.id)) {
                    idx = i;
                    break;
                }
            }
        }
        if (isShuffle && MainActivity.tracks != null && MainActivity.tracks.size() > 0) {
            idx = new java.util.Random().nextInt(MainActivity.tracks.size());
            track = MainActivity.tracks.get(idx);
            if(player != null) { player.release(); player = null; }
            titleView.setText(track.title);
            artistView.setText(track.artist);
            loadArtwork();
            initPlayer();
        } else if (idx != -1 && idx < MainActivity.tracks.size() - 1) {
            track = MainActivity.tracks.get(idx + 1);
            if(player != null) { player.release(); player = null; }
            titleView.setText(track.title);
            artistView.setText(track.artist);
            loadArtwork();
            initPlayer();
        } else {
            Toast.makeText(PlayerActivity.this, "No next track", Toast.LENGTH_SHORT).show();
        }
    }

    private void togglePlayPause() {
        if (player != null && isPrepared) {
            if (player.isPlaying()) {
                player.pause();
                playPauseBtn.setImageResource(android.R.drawable.ic_media_play);
            } else {
                player.start();
                playPauseBtn.setImageResource(android.R.drawable.ic_media_pause);
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            togglePlayPause();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private String formatTime(int ms) {
        int totalSec = ms / 1000;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format("%02d:%02d", min, sec);
    }

    @Override
    public void onBackPressed() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        MainActivity.TrackItem newTrack = (MainActivity.TrackItem) intent.getSerializableExtra("track");
        if (newTrack != null && (track == null || !track.id.equals(newTrack.id))) {
            track = newTrack;
            saveTrack(track);
            if(player != null) {
                player.release();
                player = null;
            }
            titleView.setText(track.title);
            artistView.setText(track.artist);
            loadArtwork();
            initPlayer();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateRunnable);
    }
}

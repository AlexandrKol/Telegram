package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.ui.Stories.recorder.StoryRecorder.PAGE_CAMERA;
import static org.telegram.ui.Stories.recorder.StoryRecorder.PAGE_PREVIEW;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Pair;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;

import com.google.android.exoplayer2.util.Log;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.camera.CameraController;
import org.telegram.messenger.camera.CameraView;
import org.telegram.tgnet.TLObject;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PhotoViewer;
import org.telegram.ui.Stories.DarkThemeResourceProvider;
import org.telegram.ui.Stories.recorder.DualCameraView;
import org.telegram.ui.Stories.recorder.FlashViews;
import org.telegram.ui.Stories.recorder.GalleryListView;
import org.telegram.ui.Stories.recorder.HintTextView;
import org.telegram.ui.Stories.recorder.HintView2;
import org.telegram.ui.Stories.recorder.PaintView;
import org.telegram.ui.Stories.recorder.PhotoVideoSwitcherView;
import org.telegram.ui.Stories.recorder.PlayPauseButton;
import org.telegram.ui.Stories.recorder.RecordControl;
import org.telegram.ui.Stories.recorder.StoryEntry;
import org.telegram.ui.Stories.recorder.StoryPrivacySelector;
import org.telegram.ui.Stories.recorder.StoryRecorder;
import org.telegram.ui.Stories.recorder.ToggleButton;
import org.telegram.ui.Stories.recorder.ToggleButton2;
import org.telegram.ui.Stories.recorder.VideoTimerView;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;

public class ChatAttachControl {
    private final Theme.ResourcesProvider resourcesProvider = new DarkThemeResourceProvider();
    private final FrameLayout controlContainer;
    private int flashButtonResId  = R.drawable.media_photo_flash_on2;
    private final FrameLayout actionBarContainer;

    FrameLayout container;
    Context context;
    private long botId;
    private PaintView paintView;
    private String botLang;
    private boolean fromGallery;
    private final int currentAccount;
    private File outputFile;
    private boolean isVideo = false;
    private boolean takingPhoto = false;
    private boolean takingVideo = false;
    private boolean stoppingTakingVideo = false;
    private boolean awaitingPlayer = false;
    private boolean isDark;

    private FrameLayout navbarContainer;
    Activity activity;
    private RecordControl recordControl;
    private VideoTimerView videoTimerView;

    private boolean videoTimerShown = true;
    PhotoVideoSwitcherView modeSwitcherView;
    ToggleButton2 flashButton;
    LinearLayout  actionBarButtons;
    ChatAttachAlertPhotoLayout chatlert;
    private HintTextView hintTextView;

    private ContainerView containerView;
    FlashViews.ImageViewInvertable backButton;
    private GalleryListView galleryListView;

    public void ShuwClose(float value) {
        if(value == 0){
            controlContainer.setVisibility(View.GONE);
            navbarContainer.setVisibility(View.GONE);
            actionBarContainer.setVisibility(View.GONE);
        }

        else {
            controlContainer.setVisibility(View.VISIBLE);
            navbarContainer.setVisibility(View.VISIBLE);
            actionBarContainer.setVisibility(View.VISIBLE);
        }
        controlContainer.setAlpha(value);
        navbarContainer.setAlpha(value);
    }

    private boolean onCollage = false;
    private class ContainerView extends FrameLayout {
        private int previewW, previewH;



        public ContainerView(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            // final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int H = MeasureSpec.getSize(heightMeasureSpec);
            int underControls = dp(48);
            previewH = H - underControls ;
            previewW = (int) Math.ceil(previewH * 9f / 16f);

        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            final int w = right - left;
            final int h = bottom - top;

            controlContainer.layout(0, previewH - controlContainer.getMeasuredHeight(), previewW, previewH);
            actionBarContainer.layout(0, 45,w, actionBarContainer.getMeasuredHeight());

            actionBarButtons.layout((int) (w/2 + 55), 0, w,  actionBarButtons.getMeasuredHeight());


        }
    }
    public ChatAttachControl(ChatAttachAlertPhotoLayout chatAttachAlertPhotoLayout, FrameLayout container) {
        chatlert = chatAttachAlertPhotoLayout;
        context = chatlert.getContext();

        this.container = container;
        currentAccount = UserConfig.selectedAccount;
        activity = chatlert.parentAlert.baseFragment.getParentActivity();
        controlContainer = new FrameLayout(context);

        recordControl = new RecordControl(context);
        recordControl.setDelegate(recordControlDelegate);
        recordControl.startAsVideo(isVideo);
        controlContainer.addView(recordControl, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 100, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        container.addView(containerView =new ContainerView(context));
        containerView.addView(controlContainer);
        controlContainer.setVisibility(View.GONE);
        containerView.addView(actionBarContainer = new FrameLayout(context)); // 150dp
        containerView.addView(navbarContainer = new FrameLayout(context)); // 48dp
        actionBarContainer.setVisibility(View.GONE);
        backButton = new FlashViews.ImageViewInvertable(context);
        backButton.setContentDescription(getString(R.string.AccDescrGoBack));
        backButton.setScaleType(ImageView.ScaleType.CENTER);
        backButton.setImageResource(R.drawable.delete);
        backButton.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY));
        backButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        backButton.setOnClickListener(e -> {
            if (chatlert.cameraView == null) {
                return;
            }
            chatlert.onDismiss();
            //  chatlert.parentAlert.dismissInternal();


        });
        actionBarContainer.addView(backButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.LEFT));
        actionBarButtons = new LinearLayout(context);
        actionBarButtons.setOrientation(LinearLayout.HORIZONTAL);
        actionBarButtons.setGravity(Gravity.RIGHT);
        actionBarContainer.addView(actionBarButtons, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 56, Gravity.RIGHT | Gravity.FILL_HORIZONTAL, 0, 0, 8, 0));
        flashButton = new ToggleButton2(context);
        flashButton.setBackground(Theme.createSelectorDrawable(0x20ffffff));
        flashButton.setIcon(flashButtonResId , false);
        flashButton.setOnClickListener(e -> {
            if (chatlert.cameraView == null) {
                return;
            }
            String current = getCurrentFlashMode();
            String next = getNextFlashMode();
            if (current == null || current.equals(next)) {
                return;
            }
            setCurrentFlashMode(next);
            setCameraFlashModeIcon(next, true);
        });

        videoTimerView = new VideoTimerView(context);
        showVideoTimer(false, false);
        actionBarContainer.addView(videoTimerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 45, Gravity.TOP | Gravity.FILL_HORIZONTAL, 56, 0, 56, 0));
        ToggleButton collagrButton = new ToggleButton(context, R.drawable.media_collage, R.drawable.media_collage_shadow);
        collagrButton.setOnClickListener(v->{
            if(onCollage) onCollage = false;
            else  onCollage = true;
            collagrButton.setValue(onCollage);
        });
        ToggleButton dualButton = new ToggleButton(context, R.drawable.media_dual_camera2_shadow, R.drawable.media_dual_camera2);
        dualButton.setOnClickListener(v -> {
            if (chatlert.cameraView == null ) {
                return;
            }
            chatlert.cameraView.toggleDual();
            dualButton.setValue(chatlert.cameraView.isDual());


            MessagesController.getGlobalMainSettings().edit().putInt("storydualhint", 2).apply();
            MessagesController.getGlobalMainSettings().edit().putInt("storysvddualhint", 2).apply();

        });

        actionBarButtons.addView(collagrButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.CLIP_VERTICAL));
        actionBarButtons.addView(dualButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.CLIP_VERTICAL));
        actionBarButtons.addView(flashButton, LayoutHelper.createFrame(56, 56, Gravity.TOP | Gravity.RIGHT));

        navbarContainer.setBackgroundColor(0x00000000);
        navbarContainer.setVisibility(View.GONE);
        modeSwitcherView = new PhotoVideoSwitcherView(context);
        modeSwitcherView.setOnSwitchModeListener(newIsVideo -> {

            isVideo = newIsVideo;
            showVideoTimer(isVideo, true);
            modeSwitcherView.switchMode(isVideo);
            recordControl.startAsVideo(isVideo);
        });
        modeSwitcherView.setOnSwitchingModeListener(t -> {
            recordControl.startAsVideoT(t);
        });
        navbarContainer.addView(modeSwitcherView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, dp(26), Gravity.BOTTOM |  Gravity.CENTER_HORIZONTAL));
        hintTextView = new HintTextView(context);
        navbarContainer.addView(hintTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 32, Gravity.BOTTOM |  Gravity.CENTER_HORIZONTAL, 8, 0, 8, 8));
        //  modeSwitcherView.
        recordControl.updateGalleryImage();
    }



    private int frontfaceFlashMode = -1;
    private ArrayList<String> frontfaceFlashModes;
    private void checkFrontfaceFlashModes() {
        if (frontfaceFlashMode < 0) {
            frontfaceFlashMode = MessagesController.getGlobalMainSettings().getInt("frontflash", 1);
            frontfaceFlashModes = new ArrayList<>();
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_OFF);
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_AUTO);
            frontfaceFlashModes.add(Camera.Parameters.FLASH_MODE_ON);

        }
    }


    private String getNextFlashMode() {
        if (chatlert.cameraView == null || chatlert.cameraView.getCameraSession() == null) {
            return null;
        }
        if (chatlert.cameraView.isFrontface() && !chatlert.cameraView.getCameraSession().hasFlashModes()) {
            checkFrontfaceFlashModes();
            return frontfaceFlashModes.get(frontfaceFlashMode + 1 >= frontfaceFlashModes.size() ? 0 : frontfaceFlashMode + 1);
        }
        return chatlert.cameraView.getCameraSession().getNextFlashMode();
    }
    private String getCurrentFlashMode() {
        if (chatlert.cameraView == null || chatlert.cameraView.getCameraSession() == null) {
            return null;
        }
        if (chatlert.cameraView.isFrontface() && !chatlert.cameraView.getCameraSession().hasFlashModes()) {
            checkFrontfaceFlashModes();
            return frontfaceFlashModes.get(frontfaceFlashMode);
        }
        return chatlert.cameraView.getCameraSession().getCurrentFlashMode();
    }
    private StoryEntry outputEntry;
    private final RecordControl.Delegate recordControlDelegate = new RecordControl.Delegate() {
        @Override
        public boolean canRecordAudio() {
            return requestAudioPermission();
        }
        private boolean requestAudioPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && activity != null) {
                boolean granted = activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
                if (!granted) {
                    activity.requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 112);
                    return false;
                }
            }
            return true;
        }
        @Override
        public void onPhotoShoot() {
            if (takingPhoto || awaitingPlayer ) {
                return;
            }
            //  cameraHint.hide();
            if (outputFile != null) {
                try {
                    outputFile.delete();
                } catch (Exception ignore) {}
                outputFile = null;
            }
            outputFile = StoryEntry.makeCacheFile(currentAccount, false);
            takingPhoto = true;
            checkFrontfaceFlashModes();
            isDark = false;

            takePicture(null);
        }

        private void takePicture(Utilities.Callback<Runnable> done) {
            boolean savedFromTextureView = false;

            if (!useDisplayFlashlight()) {
                chatlert.cameraView.startTakePictureAnimation(true);
            }
            if (chatlert.cameraView.isDual() && TextUtils.equals(chatlert.cameraView.getCameraSession().getCurrentFlashMode(), Camera.Parameters.FLASH_MODE_OFF)) {
                chatlert.cameraView.pauseAsTakingPicture();
                final Bitmap bitmap = chatlert.cameraView.getTextureView().getBitmap();
                try (FileOutputStream out = new FileOutputStream(outputFile.getAbsoluteFile())) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                    savedFromTextureView = true;
                } catch (Exception e) {
                    FileLog.e(e);
                }
                bitmap.recycle();
            }
            if (!savedFromTextureView) {
                takingPhoto = CameraController.getInstance().takePicture(outputFile, true, chatlert.cameraView.getCameraSessionObject(), (orientation) -> {

                    takingPhoto = false;
                    if (outputFile == null) {
                        return;
                    }
                    int w = -1, h = -1;
                    try {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(outputFile.getAbsolutePath(), opts);
                        w = opts.outWidth;
                        h = opts.outHeight;
                    } catch (Exception ignore) {}

                    int rotate = orientation == -1 ? 0 : 90;
                    if (orientation == -1) {
                        if (w > h) {
                            rotate = 270;
                        }
                    } else if (h > w && rotate != 0) {
                        rotate = 0;
                    }

                    outputEntry = StoryEntry.fromPhotoShoot(outputFile, rotate);
                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, chatlert.lastImageId--, 0, outputFile.getAbsolutePath(), orientation == -1 ? 0 : orientation, false, w, h, 0);
                    photoEntry.canDeleteAfter = true;
                    if (outputEntry != null) {
                        outputEntry.botId = botId;
                        outputEntry.botLang = botLang;
                    }
                    StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                    fromGallery = false;

                    if (done != null) {
                        done.run(()->{
                            chatlert.openPhotoViewer(photoEntry, false, false);
                            //    ShuwClose(0);
                        });
                    } else {
                        chatlert.openPhotoViewer(photoEntry, false, false);
                        //  ShuwClose(0);
                    }
                });
            } else {
                takingPhoto = false;
                outputEntry = StoryEntry.fromPhotoShoot(outputFile, 0);
                if (outputEntry != null) {
                    outputEntry.botId = botId;
                    outputEntry.botLang = botLang;
                }
                StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                fromGallery = false;

                if (done != null) {
                    chatlert.openPhotoViewer(null, false, false);
                } else {
                    chatlert.openPhotoViewer(null, false, false);
                }
            }
        }

        @Override
        public void onVideoRecordStart(boolean byLongPress, Runnable whenStarted) {
            if (takingVideo || stoppingTakingVideo || awaitingPlayer ) {
                return;
            }
            actionBarButtons.setVisibility(View.GONE);
            backButton.setVisibility(View.GONE);
            BaseFragment baseFragment = chatlert.parentAlert.baseFragment;
            if (baseFragment == null) {
                baseFragment = LaunchActivity.getLastFragment();
            }
            if (baseFragment == null || baseFragment.getParentActivity() == null) {
                return;
            }
            if (Build.VERSION.SDK_INT >= 23) {
                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    baseFragment.getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 21);
                    return ;
                }
            }

            takingVideo = true;
            if (outputFile != null) {
                try {
                    outputFile.delete();
                } catch (Exception ignore) {}
                outputFile = null;
            }
            outputFile = StoryEntry.makeCacheFile(currentAccount, true);
            checkFrontfaceFlashModes();
            isDark = false;
            //  if (cameraView.isFrontface() && frontfaceFlashMode == 1) {
            //     checkIsDark();
            //  }
            // if (useDisplayFlashlight()) {
            //   flashViews.flashIn(() -> startRecording(byLongPress, whenStarted));
            //  } else {
            modeSwitcherView.setVisibility(View.GONE);
            hintTextView.setText(getString(R.string.StoryHintSwipeToZoom), true);

            startRecording( whenStarted);
            recordControl.invalidate();
            // }
        }

        private void startRecording( Runnable whenStarted) {
            if (chatlert.cameraView == null) {
                return;
            }
            CameraController.getInstance().recordVideo(chatlert.cameraView.getCameraSessionObject(), outputFile, false, (thumbPath, duration) -> {
                if (recordControl != null) {
                    recordControl.stopRecordingLoading(true);
                }
                if (outputFile == null || chatlert.cameraView == null) {
                    return;
                }

                takingVideo = false;
                stoppingTakingVideo = false;
                hintTextView.setText(getString(R.string.StoryHintPinchToZoom), true);

                if (duration <= 800) {
                    //   animateRecording(false, true);
                    //  setAwakeLock(false);
                    videoTimerView.setRecording(false, true);
                    if (recordControl != null) {
                        recordControl.stopRecordingLoading(true);
                    }
                    try {
                        outputFile.delete();
                        outputFile = null;
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (thumbPath != null) {
                        try {
                            new File(thumbPath).delete();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    }
                    return;
                }

                showVideoTimer(false, true);

                outputEntry = StoryEntry.fromVideoShoot(outputFile, thumbPath, duration);
                if (outputEntry != null) {
                    outputEntry.botId = botId;
                    outputEntry.botLang = botLang;
                }
                StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                fromGallery = false;
                int width = chatlert.cameraView.getVideoWidth(), height = chatlert.cameraView.getVideoHeight();
                if (width > 0 && height > 0) {
                    outputEntry.width = width;
                    outputEntry.height = height;
                    outputEntry.setupMatrix();
                }

                navigateToPreviewWithPlayerAwait(() -> {
                    try {
                        BitmapFactory.Options options = new BitmapFactory.Options();
                        options.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(new File(thumbPath).getAbsolutePath(), options);
                    } catch (Exception ignore) {}
                    MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, chatlert.lastImageId--, 0, outputFile.getAbsolutePath(), 0, true, width, height, 0);
                    photoEntry.duration = (int) (duration / 1000f);
                    photoEntry.thumbPath = thumbPath;
                    if (chatlert.parentAlert.avatarPicker != 0 && chatlert.cameraView.isFrontface()) {
                        photoEntry.cropState = new MediaController.CropState();
                        photoEntry.cropState.mirrored = true;
                        photoEntry.cropState.freeform = false;
                        photoEntry.cropState.lockedAspectRatio = 1.0f;
                    }
                    chatlert.openPhotoViewer(photoEntry, false, false);
                    isVideo = false;
                    recordControl.startAsVideo(isVideo);
                    modeSwitcherView.switchMode(isVideo);
                    actionBarButtons.setVisibility(View.VISIBLE);
                    backButton.setVisibility(View.VISIBLE);
                    AndroidUtilities.runOnUIThread(() -> {
                        hintTextView.setText("", false);
                        modeSwitcherView.setVisibility(View.VISIBLE);
                    },   400);
                }, 0);

            }, () -> {



                whenStarted.run();

                //  animateRecording(true, true);
                // setAwakeLock(true);

                videoTimerView.setRecording(true, true);
            }, chatlert.cameraView, true);

            if (!isVideo) {
                isVideo = true;
                showVideoTimer(isVideo, true);
                modeSwitcherView.switchMode(isVideo);
                recordControl.startAsVideo(isVideo);

            }
        }

        @Override
        public void onVideoRecordLocked() {
            hintTextView.setText(getString(R.string.StoryHintPinchToZoom), true);
        }

        @Override
        public void onVideoRecordPause() {

        }

        @Override
        public void onVideoRecordResume() {

        }

        @Override
        public void onVideoRecordEnd(boolean byDuration) {
            if (stoppingTakingVideo || !takingVideo) {
                return;
            }
            stoppingTakingVideo = true;

            AndroidUtilities.runOnUIThread(() -> {

                if (takingVideo && stoppingTakingVideo && chatlert.cameraView!= null) {



                    //    showZoomControls(false, true);
//                    animateRecording(false, true);
//                    setAwakeLock(false);
                    CameraController.getInstance().stopVideoRecording(chatlert.cameraView.getCameraSessionRecording(), false, false);
                    if(outputFile != null)
                        chatlert.cameraView.runHaptic();
                }

            }, byDuration ? 0 : 400);
        }

        @Override
        public void onVideoDuration(long duration) {
            videoTimerView.setDuration(duration, true);
        }

        @Override
        public void onGalleryClick() {
            if ( !takingPhoto && !takingVideo //&& requestGalleryPermission()
            ) {
                animateGalleryListView(true);
            }

        }

        @Override
        public void onFlipClick() {
            if (chatlert.cameraView == null || awaitingPlayer || takingPhoto || !chatlert.cameraView.isInited() ) {
                return;
            }
            //  if (savedDualHint != null) {
            //  savedDualHint.hide();
            // }
            if (useDisplayFlashlight() && frontfaceFlashModes != null && !frontfaceFlashModes.isEmpty()) {
                final String mode = frontfaceFlashModes.get(frontfaceFlashMode);
                SharedPreferences sharedPreferences = ApplicationLoader.applicationContext.getSharedPreferences("camera", Activity.MODE_PRIVATE);
                sharedPreferences.edit().putString("flashMode", mode).commit();
            }
            chatlert.cameraView.switchCamera();
            //saveCameraFace(cameraView.isFrontface());
            if (useDisplayFlashlight()) {
                //flashViews.flashIn(null);
            } else {
                // flashViews.flashOut();
            }
        }

        @Override
        public void onFlipLongClick() {
            if (chatlert.cameraView != null) {
                chatlert.cameraView.toggleDual();
            }
        }

        @Override
        public void onZoom(float zoom) {
            //  zoomControlView.setZoom(zoom, true);
            // showZoomControls(false, true);
        }
    };
    private boolean useDisplayFlashlight() {
        return (takingPhoto || takingVideo)  && (frontfaceFlashMode == 2 || frontfaceFlashMode == 1 && isDark);
    }

    private ValueAnimator galleryOpenCloseAnimator;
    private SpringAnimation galleryOpenCloseSpringAnimator;
    private Boolean galleryListViewOpening;
    private Runnable galleryLayouted;
    private Parcelable lastGalleryScrollPosition;
    private MediaController.AlbumEntry lastGallerySelectedAlbum;
    private boolean applyContainerViewTranslation2 = true;
    private ValueAnimator containerViewBackAnimator;
    private void animateGalleryListView(boolean open) {
        if (galleryListView == null) {
            if (open) {
                createGalleryListView(false);
            }
            if (galleryListView == null) {
                return;
            }
        }
        if (galleryListView.firstLayout) {
            galleryLayouted = () -> animateGalleryListView(open);
            return;
        }

        if (galleryOpenCloseAnimator != null) {
            galleryOpenCloseAnimator.cancel();
            galleryOpenCloseAnimator = null;
        }
        if (galleryOpenCloseSpringAnimator != null) {
            galleryOpenCloseSpringAnimator.cancel();
            galleryOpenCloseSpringAnimator = null;
        }

        if (galleryListView == null) {
            if (open) {
                createGalleryListView(false);
            }
            if (galleryListView == null) {
                return;
            }
        }
        if (galleryListView != null) {
            galleryListView.ignoreScroll = false;
        }

        galleryListViewOpening = open;
        float from = galleryListView.getTranslationY();
        float to = open ? 0 : containerView.getHeight() - galleryListView.top() + AndroidUtilities.navigationBarHeight * 2.5f;
        float fulldist = Math.max(1, containerView.getHeight());

        galleryListView.ignoreScroll = !open;

        applyContainerViewTranslation2 = containerViewBackAnimator == null;
        if (open) {
            galleryOpenCloseSpringAnimator = new SpringAnimation(galleryListView, DynamicAnimation.TRANSLATION_Y, to);
            galleryOpenCloseSpringAnimator.getSpring().setDampingRatio(0.75f);
            galleryOpenCloseSpringAnimator.getSpring().setStiffness(350.0f);
            galleryOpenCloseSpringAnimator.addEndListener((a, canceled, c, d) -> {
                if (canceled) {
                    return;
                }
                galleryListView.setTranslationY(to);
                galleryListView.ignoreScroll = false;
                galleryOpenCloseSpringAnimator = null;
                galleryListViewOpening = null;
            });
            galleryOpenCloseSpringAnimator.start();
        } else {
            galleryOpenCloseAnimator = ValueAnimator.ofFloat(from, to);
            galleryOpenCloseAnimator.addUpdateListener(anm -> {
                galleryListView.setTranslationY((float) anm.getAnimatedValue());
            });
            galleryOpenCloseAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    containerView.removeView(galleryListView);
                    galleryListView = null;
                    galleryOpenCloseAnimator = null;
                    galleryListViewOpening = null;
                }
            });
            galleryOpenCloseAnimator.setDuration(450L);
            galleryOpenCloseAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
            galleryOpenCloseAnimator.start();
        }

        if (!open && !awaitingPlayer) {
            lastGalleryScrollPosition = null;
        }

    }
    private void createGalleryListView(boolean forAddingPart) {
        if (galleryListView != null || context == null) {
            return;
        }
        galleryListView = new GalleryListView(currentAccount, context, resourcesProvider, lastGallerySelectedAlbum, forAddingPart) {

            @Override
            public void firstLayout() {
                galleryListView.setTranslationY(containerView.getMeasuredHeight() - galleryListView.top());
                if (galleryLayouted != null) {
                    galleryLayouted.run();
                    galleryLayouted = null;
                }
            }


            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                if (ev.getAction() == MotionEvent.ACTION_DOWN && ev.getY() < top()) {
                    animateGalleryListView(false);
                    return true;
                }
                return super.dispatchTouchEvent(ev);
            }
        };
        galleryListView.allowSearch(false);
        galleryListView.setOnBackClickListener(() -> {
            animateGalleryListView(false);
        });
        galleryListView.setOnSelectListener((entry, blurredBitmap) -> {
            if (entry == null || galleryListViewOpening != null //|| scrollingY || !isGalleryOpen()
            ) {
                return;
            }

            if (forAddingPart) {
                if (outputEntry == null) {
                    return;
                }
                outputEntry.editedMedia = true;
                if (entry instanceof MediaController.PhotoEntry) {

                } else if (entry instanceof TLObject) {

                }
                animateGalleryListView(false);
            } else {
                if (entry instanceof MediaController.PhotoEntry) {
                    MediaController.PhotoEntry photoEntry = (MediaController.PhotoEntry) entry;
                    isVideo = photoEntry.isVideo;
                    outputEntry = StoryEntry.fromPhotoEntry(photoEntry);

                    StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                    outputEntry.blurredVideoThumb = blurredBitmap;
                    fromGallery = true;
                } else if (entry instanceof StoryEntry) {
                    StoryEntry storyEntry = (StoryEntry) entry;
                    if (storyEntry.file == null) {
                        MessagesController.getInstance(currentAccount).getStoriesController().getDraftsController().delete(storyEntry);
                        return;
                    }

                    isVideo = storyEntry.isVideo;
                    outputEntry = storyEntry;
                    outputEntry.blurredVideoThumb = blurredBitmap;
                    fromGallery = false;
                }

                if (outputEntry != null) {
                    outputEntry.botId = botId;
                    outputEntry.botLang = botLang;
                    outputEntry.setupMatrix();
                }

                showVideoTimer(false, true);
                modeSwitcherView.switchMode(isVideo);
                recordControl.startAsVideo(isVideo);

                animateGalleryListView(false);
                int width = chatlert.cameraView.getVideoWidth(), height = chatlert.cameraView.getVideoHeight();

                if (outputEntry != null) {
                    outputEntry.botId = botId;
                    outputEntry.botLang = botLang;
                }
                StoryPrivacySelector.applySaved(currentAccount, outputEntry);

                outputFile = outputEntry.file;
                MediaController.PhotoEntry photoEntry = new MediaController.PhotoEntry(0, chatlert.lastImageId--, 0, outputFile.getAbsolutePath(), 0, true, width, height, 0);
                photoEntry.canDeleteAfter = true;
                photoEntry.isVideo = outputEntry.isVideo;
                StoryPrivacySelector.applySaved(currentAccount, outputEntry);
                chatlert.openPhotoViewer(photoEntry, false, false);
            }

            if (galleryListView != null) {
                lastGalleryScrollPosition = galleryListView.layoutManager.onSaveInstanceState();
                lastGallerySelectedAlbum = galleryListView.getSelectedAlbum();
            }
        });
        if (lastGalleryScrollPosition != null) {
            galleryListView.layoutManager.onRestoreInstanceState(lastGalleryScrollPosition);
        }
        containerView.addView(galleryListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
    }
    private void showVideoTimer(boolean show, boolean animated) {
        if (videoTimerShown == show) {
            return;
        }

        videoTimerShown = show;
        if (animated) {
            videoTimerView.animate().alpha(show ? 1 : 0).setDuration(350).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).withEndAction(() -> {
                if (!show) {
                    videoTimerView.setRecording(false, false);
                }
            }).start();
        } else {
            videoTimerView.clearAnimation();
            videoTimerView.setAlpha(show ? 1 : 0);
            if (!show) {
                videoTimerView.setRecording(false, false);
            }
        }
    }
    public void navigateToPreviewWithPlayerAwait(Runnable open, long seekTo) {
        navigateToPreviewWithPlayerAwait(open, seekTo, 800);
    }
    private void setCurrentFlashMode(String mode) {
        if (chatlert.cameraView == null || chatlert.cameraView.getCameraSession() == null) {
            return;
        }
        if (chatlert.cameraView.isFrontface() && !chatlert.cameraView.getCameraSession().hasFlashModes()) {
            int index = frontfaceFlashModes.indexOf(mode);
            if (index >= 0) {
                frontfaceFlashMode = index;
                MessagesController.getGlobalMainSettings().edit().putInt("frontflash", frontfaceFlashMode).apply();
            }
            return;
        }
        chatlert.cameraView.getCameraSession().setCurrentFlashMode(mode);
    }
    private void setCameraFlashModeIcon(String mode, boolean animated) {
        flashButton.clearAnimation();
        if (chatlert.cameraView != null && chatlert.cameraView.isDual() ) {
            mode = null;
        }
        if (mode == null) {
            if (animated) {
                flashButton.setVisibility(View.VISIBLE);
                flashButton.animate().alpha(0).withEndAction(() -> {
                    flashButton.setVisibility(View.GONE);
                }).start();
            } else {
                flashButton.setVisibility(View.GONE);
                flashButton.setAlpha(0f);
            }
            return;
        }
        final int resId;
        switch (mode) {
            case Camera.Parameters.FLASH_MODE_ON:
                resId = R.drawable.media_photo_flash_on2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOn));
                break;
            case Camera.Parameters.FLASH_MODE_AUTO:
                resId = R.drawable.media_photo_flash_auto2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashAuto));
                break;
            default:
            case Camera.Parameters.FLASH_MODE_OFF:
                resId = R.drawable.media_photo_flash_off2;
                flashButton.setContentDescription(getString(R.string.AccDescrCameraFlashOff));
                break;
        }
        flashButton.setIcon(flashButtonResId = resId, animated && flashButtonResId != resId);
        flashButton.setVisibility(View.VISIBLE);
        if (animated) {
            flashButton.animate().alpha(1f).start();
        } else {
            flashButton.setAlpha(1f);
        }
    }
    private Runnable afterPlayerAwait;
    private boolean previewAlreadySet;
    public void navigateToPreviewWithPlayerAwait(Runnable open, long seekTo, long ms) {
        if (awaitingPlayer || outputEntry == null) {
            return;
        }
        if (afterPlayerAwait != null) {
            AndroidUtilities.cancelRunOnUIThread(afterPlayerAwait);
        }
        previewAlreadySet = true;
        awaitingPlayer = true;
        afterPlayerAwait = () -> {
            AndroidUtilities.cancelRunOnUIThread(afterPlayerAwait);
            afterPlayerAwait = null;
            awaitingPlayer = false;
            open.run();
        };
        AndroidUtilities.runOnUIThread(afterPlayerAwait, ms);
    }
}
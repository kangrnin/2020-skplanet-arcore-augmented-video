package com.example.arvideo_test;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.ExternalTexture;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;

public class MainActivity extends AppCompatActivity {

    ArFragment arFragment;
    Session session;
    Scene scene;

    MediaPlayer mediaPlayer;
    ExternalTexture texture;

    AugmentedImage currentTrackingImage;

    private ModelRenderable videoRenderable;
    private float HEIGHT = 0.33f;

    private boolean tracking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        texture = new ExternalTexture();
        mediaPlayer = MediaPlayer.create(this, R.raw.vid1);
        mediaPlayer.setSurface(texture.getSurface());
        mediaPlayer.setLooping(false);

        ModelRenderable.builder()
                .setSource(this,R.raw.video_screen)
                .build()
                .thenAccept(modelRenderable -> {
                    videoRenderable = modelRenderable;
                    videoRenderable.getMaterial().setExternalTexture("videoTexture", texture);
                    videoRenderable.getMaterial().setFloat4("keyColor", new Color(0.01843f, 1.0f, 0.098f));
                });

        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);

        try {
            session = new Session(/* context= */ this);
        } catch (Exception e) {
            Log.e("error", "Error while creating Session");
        }
        arFragment.getArSceneView().setupSession(session);

        scene = arFragment.getArSceneView().getScene();
        scene.addOnUpdateListener(this::onUpdateFrame);

        Config config = new Config(session);
        config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
        setupAugmentedImageDatabase(config);
        session.configure(config);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void onUpdateFrame(FrameTime frameTime) {
        Frame frame = arFragment.getArSceneView().getArFrame();

        if(frame == null)
            return;

        Collection<AugmentedImage> updatedAugmentedImages =
                frame.getUpdatedTrackables(AugmentedImage.class);

        AugmentedImage beforeTrackingImage = currentTrackingImage;
        for (AugmentedImage augmentedImage : updatedAugmentedImages) {
            switch (augmentedImage.getTrackingState()) {
                case TRACKING:
                    if(!tracking && currentTrackingImage == null || currentTrackingImage == augmentedImage) {
                        currentTrackingImage = augmentedImage;
                        scene.addChild(createVideoNode(augmentedImage));

                        tracking = true;
                        break;
                    }
                case STOPPED:
                    currentTrackingImage = null;
                    break;
            }
        }

        AugmentedImage afterTrackingImage = currentTrackingImage;
        if(beforeTrackingImage != afterTrackingImage) {
            mediaPlayer = MediaPlayer.create(this, R.raw.vid1);
            mediaPlayer.setSurface(texture.getSurface());
        }
    }

    private AnchorNode createVideoNode(AugmentedImage augmentedImage) {
        AnchorNode anchorNode = new AnchorNode(augmentedImage.createAnchor(augmentedImage.getCenterPose()));

        mediaPlayer.start();
        texture.getSurfaceTexture().setOnFrameAvailableListener(surfaceTexture -> {
            anchorNode.setRenderable(videoRenderable);
            anchorNode.setLocalScale(new Vector3(
                    augmentedImage.getExtentX(), 1.0f, augmentedImage.getExtentZ()));
        });
        return anchorNode;
    }

    private boolean setupAugmentedImageDatabase(Config config) {
        HashMap<String, String> fileNames = new HashMap<>();
        fileNames.put("img1.png", "vid1.mp4");
//        fileNames.put("img2.png", "vid2.mp4");
//        fileNames.put("img3.png", "vid3.mp4");

        AugmentedImageDatabase augmentedImageDatabase = new AugmentedImageDatabase(session);
        ArrayList<Bitmap> augmentedImageBitmap = new ArrayList<>();
        ArrayList<Bitmap> bitmaps = new ArrayList<>();

        for(String imgName: fileNames.keySet()) {
            try (InputStream is = getAssets().open(imgName)) {
                augmentedImageDatabase.addImage(fileNames.get(imgName), BitmapFactory.decodeStream(is));
            } catch (IOException e) {
                Log.e("error", "IOError on loading Bitmap");
                return false;
            }
        }

        if (augmentedImageBitmap == null)
            return false;

        config.setAugmentedImageDatabase(augmentedImageDatabase);
        return true;
    }
}
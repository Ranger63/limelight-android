package com.limelight.nvstream.av.video;

import java.nio.ByteBuffer;

import com.limelight.nvstream.av.AvByteBufferDescriptor;
import com.limelight.nvstream.av.AvDecodeUnit;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaCodec.BufferInfo;
import android.os.Build;
import android.view.Surface;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class MediaCodecDecoderRenderer implements DecoderRenderer {

	private ByteBuffer[] videoDecoderInputBuffers;
	private MediaCodec videoDecoder;
	private Thread rendererThread;

	public static boolean hasWhitelistedDecoder() {
		for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
			MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
			
			// Skip encoders
			if (codecInfo.isEncoder()) {
				continue;
			}
			
			if (codecInfo.getName().equalsIgnoreCase("omx.qcom.video.decoder.avc")) {
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public void setup(int width, int height, Surface renderTarget) {
		videoDecoder = MediaCodec.createDecoderByType("video/avc");
		MediaFormat videoFormat = MediaFormat.createVideoFormat("video/avc", width, height);

		videoDecoder.configure(videoFormat, renderTarget, null, 0);

		videoDecoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);
		
		videoDecoder.start();

		videoDecoderInputBuffers = videoDecoder.getInputBuffers();
	}
	
	private void startRendererThread()
	{
		rendererThread = new Thread() {
			@Override
			public void run() {
				long nextFrameTimeUs = 0;
				while (!isInterrupted())
				{
					BufferInfo info = new BufferInfo();
					int outIndex = videoDecoder.dequeueOutputBuffer(info, 100);
				    switch (outIndex) {
				    case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
				    	System.out.println("Output buffers changed");
					    break;
				    case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
				    	System.out.println("Output format changed");
				    	System.out.println("New output Format: " + videoDecoder.getOutputFormat());
				    	break;
				    default:
				      break;
				    }
				    if (outIndex >= 0) {
				    	boolean render = false;
				    	
				    	if (currentTimeUs() >= nextFrameTimeUs) {
				    		render = true;
				    		nextFrameTimeUs = computePresentationTime(60);
				    	}
				    	
				    	videoDecoder.releaseOutputBuffer(outIndex, render);
				    }
				}
			}
		};
		rendererThread.start();
	}
	
	private static long currentTimeUs() {
		return System.nanoTime() / 1000;
	}

	private long computePresentationTime(int frameRate) {
		return currentTimeUs() + (1000000 / frameRate);
	}

	@Override
	public void start() {
		startRendererThread();
	}

	@Override
	public void stop() {
		rendererThread.interrupt();
	}

	@Override
	public void release() {
		if (videoDecoder != null) {
			videoDecoder.release();
		}
	}

	@Override
	public void submitDecodeUnit(AvDecodeUnit decodeUnit) {
		if (decodeUnit.getType() != AvDecodeUnit.TYPE_H264) {
			System.err.println("Unknown decode unit type");
			return;
		}
		
		int inputIndex = videoDecoder.dequeueInputBuffer(-1);
		if (inputIndex >= 0)
		{
			ByteBuffer buf = videoDecoderInputBuffers[inputIndex];
			
			// Clear old input data
			buf.clear();
			
			// Copy data from our buffer list into the input buffer
			for (AvByteBufferDescriptor desc : decodeUnit.getBufferList())
			{
				buf.put(desc.data, desc.offset, desc.length);
			}
			
			videoDecoder.queueInputBuffer(inputIndex,
						0, decodeUnit.getDataLength(),
						0, decodeUnit.getFlags());
		}
	}
}
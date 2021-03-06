/****************************************************************************
 *
 *   Copyright (c) 2017 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/

package com.comino.slam.detectors.impl;


import java.util.concurrent.TimeUnit;

import org.mavlink.messages.MSP_CMD;
import org.mavlink.messages.lquac.msg_msp_command;
import org.mavlink.messages.lquac.msg_msp_micro_slam;

import com.comino.mav.control.IMAVMSPController;
import com.comino.msp.main.MSPConfig;
import com.comino.msp.main.control.listener.IMAVLinkListener;
import com.comino.msp.model.DataModel;
import com.comino.msp.utils.ExecutorService;
import com.comino.msp.utils.MSPMathUtils;
import com.comino.server.mjpeg.impl.HttpMJPEGHandler;
import com.comino.slam.boofcv.odometry.MAVDepthVisualOdometry;
import com.comino.slam.detectors.ISLAMDetector;
import com.comino.slam.vfh.VfhHist;
import com.comino.slam.vfh.vfh2D.HistogramGrid2D;
import com.comino.slam.vfh.vfh2D.PolarHistogram2D;

import boofcv.struct.geo.Point2D3D;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;

public class VfhDepthDetector implements ISLAMDetector, Runnable {

	private float     min_distance     = 2.25f;
	private float     min_altitude     = 0.2f;

	private DataModel     model        = null;
	private Point2D3D     test         = new Point2D3D();

	private HistogramGrid2D  vfh = null;
	private PolarHistogram2D poh = null;

	private IMAVMSPController control = null;

	public VfhDepthDetector(IMAVMSPController control, MSPConfig config,HttpMJPEGHandler streamer) {

		this.control  = control;
		this.model   = control.getCurrentModel();

		this.min_distance = config.getFloatProperty("min_distance", "1.25f");
		System.out.println("[col] Planning distance set to "+min_distance);
		this.min_altitude = config.getFloatProperty("min_altitude", "0.3f");
		System.out.println("[col] Min.altitude set to "+min_altitude);

		this.vfh      = new HistogramGrid2D(10,10,20,min_distance/2,model.grid.getResolution());
		this.poh      = new PolarHistogram2D(2,2,10f,0.0025f, model.grid.getResolution());

		ExecutorService.get().scheduleAtFixedRate(this, 5000, 200, TimeUnit.MILLISECONDS);

		control.registerListener(msg_msp_command.class, new IMAVLinkListener() {
			@Override
			public void received(Object o) {
				msg_msp_command cmd = (msg_msp_command)o;
				switch(cmd.command) {
				case MSP_CMD.MSP_TRANSFER_MICROSLAM:
					model.grid.invalidateTransfer();
					break;
				}
			}
		});

		streamer.registerOverlayListener(ctx -> {
			ctx.drawOval((int)test.observation.x, (int)test.observation.y, 5, 5);

		});

	}

	@Override
	public void process(MAVDepthVisualOdometry<GrayU8,GrayU16> odometry, GrayU16 depth, GrayU8 gray) {


		test.setLocation(odometry.getPoint3DFromPixel(gray.width/2, gray.height/2));
		test.getObservation().set(160, 120);
		if(test.location!=null)
		    System.out.println(test.location.z);

	}


	@Override
	public void run() {
		poh.histUpdate(vfh.getMovingWindow(model.state.l_x, model.state.l_y));
		VfhHist smoothed = poh.histSmooth(5);
		int vi = poh.selectValley(smoothed, (int)MSPMathUtils.fromRad(model.attitude.y));
		vfh.forget();
		vfh.transferGridToModel(model, 10, false);

		// publish planned data
		msg_msp_micro_slam msg = new msg_msp_micro_slam(2,1);
		msg.pd = MSPMathUtils.toRad(poh.getDirection(smoothed, vi, 18));
		msg.pv = 0;
		msg.tms = System.nanoTime() / 1000;
		control.sendMAVLinkMessage(msg);

	}

	@Override
	public void reset(float x, float y, float z) {


	}

}

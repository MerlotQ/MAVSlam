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


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.mavlink.messages.MAV_SEVERITY;
import org.mavlink.messages.MSP_CMD;
import org.mavlink.messages.lquac.msg_msp_command;

import com.comino.mav.control.IMAVMSPController;
import com.comino.msp.main.MSPConfig;
import com.comino.msp.main.control.listener.IMAVLinkListener;
import com.comino.msp.model.DataModel;
import com.comino.msp.model.segment.LogMessage;
import com.comino.server.mjpeg.impl.HttpMJPEGHandler;
import com.comino.slam.boofcv.odometry.MAVDepthVisualOdometry;
import com.comino.slam.detectors.ISLAMDetector;

import boofcv.abst.sfm.AccessPointTracks3D;
import boofcv.struct.geo.Point2D3D;
import boofcv.struct.image.GrayU16;
import boofcv.struct.image.GrayU8;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point3D_F64;
import georegression.struct.se.Se3_F64;
import georegression.transform.se.SePointOps_F64;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

public class SimpleCollisionDetector implements ISLAMDetector {

	private final static int MIN_POINTS = 5;

	private float     min_distance     = 2.25f;
	private float     min_altitude     = 0.2f;

	private DataModel     model        = null;
	private Point3D_F64   pos          = new Point3D_F64();
	private Point3D_F64   p_ned        = new Point3D_F64();
	private Point2D3D     center_ned   = new Point2D3D();

	private Se3_F64 current         = new Se3_F64();

	private long last_clean         = 0;

	private BooleanProperty collision = new SimpleBooleanProperty(false);

	private List<Point2D3D> nearestPoints =  new ArrayList<Point2D3D>();

	public SimpleCollisionDetector(IMAVMSPController control, MSPConfig config,HttpMJPEGHandler streamer) {

		this.model    = control.getCurrentModel();
		this.min_distance = config.getFloatProperty("min_distance", "1.25f");
		System.out.println("[col] Collision distance set to "+min_distance);
		this.min_altitude = config.getFloatProperty("min_altitude", "0.3f");
		System.out.println("[col] Min.altitude set to "+min_altitude);

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
			if(collision.get() && nearestPoints.size()>0) {
				for(Point2D3D n : nearestPoints) {
					ctx.drawRect((int)n.observation.x-10, (int)n.observation.y-10, 20, 20);
				}

				Point2D3D n = center_ned; //nearestPoints.get(0);
				ctx.drawString(String.format("Distance: %#.2fm", n.getLocation().z), 5, 20);

				ctx.drawOval((int)center_ned.observation.x-10, (int)center_ned.observation.y-10, 20, 20);
				ctx.drawOval((int)center_ned.observation.x-15, (int)center_ned.observation.y-15, 30, 30);
			}
		});

		collision.addListener((l,ov,nv) -> {
			if(nv.booleanValue()) {
				control.writeLogMessage(new LogMessage("[vis] collision warning",
						MAV_SEVERITY.MAV_SEVERITY_WARNING));

			}
			else
				control.writeLogMessage(new LogMessage("[vis] collision warning cleared",
						MAV_SEVERITY.MAV_SEVERITY_NOTICE));

		});
	}

	@Override
	public void process(MAVDepthVisualOdometry<GrayU8,GrayU16> odometry, GrayU16 depth, GrayU8 gray) {
		Point2D_F64 xy; Point3D_F64 p;

		AccessPointTracks3D points = (AccessPointTracks3D)odometry;

		nearestPoints.clear();

//		if(points.getAllTracks().size()==0 || ( model.raw.di < min_altitude)) {
//			collision.set(false);
//			return;
//		}

		// SLAM forgets old blocks after 2 mins
		if((System.nanoTime()-last_clean)>120 * 1e9) {
			model.grid.getData().forEach((i,b) -> {
				if(b.tms<last_clean) {
                   model.grid.setBlock(i, false);
				}
			});
			last_clean = System.nanoTime();
		}

		center_ned.location.set(0,0,0); center_ned.observation.set(0,0);
		current = odometry.getCameraToWorld();

		for( int i = 0; i < points.getAllTracks().size(); i++ ) {
			if(points.isInlier(i)) {
				// xy is the observation
				xy = points.getAllTracks().get(i);
				// p is the obstacle location in body-frame
				p = odometry.getTrackLocation(i);

				if(p.z < min_distance) {

					Point2D3D n = new Point2D3D();
					n.setLocation(p);
					n.setObservation(xy);

					SePointOps_F64.transform(current,p,p_ned);

					pos.x = p_ned.z + model.state.l_x - current.T.z;
					pos.y = p_ned.x + model.state.l_y - current.T.x;
					pos.z = -(p_ned.y - current.T.y) + model.state.l_z;

					if(Math.abs(pos.z - model.state.l_z) < 0.5 && model.raw.di >0.5) {
						model.grid.setBlock(pos.x , pos.y);
					}

					nearestPoints.add(n);
					center_ned.location.plusIP(p_ned);
					center_ned.observation.plusIP(xy);
				}
			}
		}
		if(nearestPoints.size()>MIN_POINTS) {

			center_ned.location.scale(1.0f/nearestPoints.size());
			center_ned.observation.scale(1.0f/nearestPoints.size());

			Collections.sort(nearestPoints, (a, b) -> {
				return Double.compare(a.location.z,b.location.z);
			});

			pos.x =   center_ned.location.z + model.state.l_x - current.T.z;
			pos.y =   center_ned.location.x + model.state.l_y - current.T.x;
			pos.z = -(center_ned.location.y - current.T.y) + model.state.l_z;

			if(Math.abs(pos.z - model.state.l_z) < 0.5 && model.raw.di >0.5) {
				model.grid.setIndicator(pos.x , pos.y);
				collision.set(true);
			} else
				collision.set(false);

		} else
			collision.set(false);

	}


	public void reset(float x, float y, float z) {
		//origin.set(y,x,x);
		//model.slam.moveTo(x, y, z);
		nearestPoints.clear();
	}

}

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

/*
 * Source: http://crsouza.blogspot.com/2009/09/modulo-and-modular-distance-in-c.html
 */

package com.comino.slam.vfh;

public class VfhUtils {

	public static int modulo(int x, int m) {
		int r;
		if (m < 0) m = -m;
		r = x % m;
		return r < 0 ? r + m : r;
	}

	public static int modularDist(int a, int b, int m) {
		int dist_a = modulo(a - b, m);
		int dist_b = modulo(b - a, m);

		return dist_a < dist_b? dist_a : dist_b;
	}

	public static void mprint(VfhGrid grid) {
		int dim = grid.dimension;

		for (int i = 0; i < dim; ++i) {
			for (int j = 0; j < dim; ++j) {
				if (grid.cells[i * dim + j] >= 8) {
					System.out.printf("o");
				} else if (grid.cells[i * dim + j] >= 4) {
					System.out.printf("x");
				} else {
					System.out.printf(" ");
				}
			}
			System.out.printf("\n");
		}
	}

}

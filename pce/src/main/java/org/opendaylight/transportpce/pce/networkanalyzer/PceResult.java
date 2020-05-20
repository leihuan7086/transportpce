/*
 * Copyright © 2017 AT&T, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.transportpce.pce.networkanalyzer;

import org.opendaylight.transportpce.common.ResponseCodes;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.pathdescription.rev171017.path.description.AToZDirection;
import org.opendaylight.yang.gen.v1.http.org.transportpce.b.c._interface.pathdescription.rev171017.path.description.ZToADirection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PceResult {
    private static final Logger LOG = LoggerFactory.getLogger(PceResult.class);
    private String calcMessage = "503 Calculator Unavailable";
    private boolean calcStatus = false;
    private String responseCode = ResponseCodes.RESPONSE_FAILED;
    private long resultWavelength = -1;
    // for now it is constant returned as received from A-end
    private long rate = -1;

    public enum LocalCause {
        NONE, TOO_HIGH_LATENCY, NO_PATH_EXISTS, INT_PROBLEM;
    }

    private LocalCause localCause = LocalCause.NONE;

    private AToZDirection atozdirection = null;
    private ZToADirection ztoadirection = null;

    public PceResult() {
    }

    public void setRC(String rc) {
        switch (rc) {
            case ResponseCodes.RESPONSE_OK :
                calcMessage = "Path is calculated";
                calcStatus = true;
                responseCode = ResponseCodes.RESPONSE_OK;
                break;
            case ResponseCodes.RESPONSE_FAILED :
                responseCode = ResponseCodes.RESPONSE_FAILED;
                calcStatus = false;
                calcMessage = "No path available";
                break;
            default:
                LOG.error("setRC: RespondeCodes unknown");
        }
    }

    public String toString() {
        return ("[" + calcMessage + "] code:[" + responseCode + "] wavelength="
                + resultWavelength + " localCause=" + localCause + " rate="
                + rate);
    }

    public boolean getStatus() {
        return calcStatus;
    }

    public String getMessage() {
        return calcMessage;
    }

    public String getResponseCode() {
        return responseCode;
    }

    public long getResultWavelength() {
        return resultWavelength;
    }

    public void setResultWavelength(long resultWavelength) {
        this.resultWavelength = resultWavelength;
    }

    public AToZDirection getAtoZDirection() {
        return atozdirection;
    }

    public ZToADirection getZtoADirection() {
        return ztoadirection;
    }

    public void setAtoZDirection(AToZDirection atozDirection) {
        this.atozdirection = atozDirection;
    }

    public void setZtoADirection(ZToADirection ztoaDirection) {
        this.ztoadirection = ztoaDirection;
    }

    public long getRate() {
        return rate;
    }

    public void setRate(long rate) {
        this.rate = rate;
    }

    public LocalCause getLocalCause() {
        return localCause;
    }

    public void setLocalCause(LocalCause lc) {
        // For now keep the very first fatal problem
        // TODO. Later this value might become history of algo if all
        // significant problems are added here as to List
        if (localCause == LocalCause.NONE) {
            this.localCause = lc;
        }
    }

    public void setCalcMessage(String calcMessage) {
        this.calcMessage = calcMessage;
    }

}

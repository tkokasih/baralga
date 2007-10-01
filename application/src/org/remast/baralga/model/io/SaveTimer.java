package org.remast.baralga.model.io;

import java.util.TimerTask;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.remast.baralga.model.PresentationModel;

public class SaveTimer extends TimerTask {
    
    /** The logger. */
    private static final Log log = LogFactory.getLog(SaveTimer.class);

    private PresentationModel model;

    public SaveTimer(final PresentationModel model) {
        this.model = model;
    }

    @Override
    public void run() {
        try {
            this.model.save();
        } catch (Exception e) {
            log.error(e, e);
        }
    }

}

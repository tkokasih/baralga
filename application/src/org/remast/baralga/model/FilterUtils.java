package org.remast.baralga.model;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.remast.baralga.gui.Settings;
import org.remast.baralga.model.filter.Filter;

/**
 * @author remast
 */
public abstract class FilterUtils {

    /** The logger. */
    private static final Log log = LogFactory.getLog(FilterUtils.class);

    public static Filter restoreFromSettings() {
        Filter filter = new Filter();
        
        final String selectedMonth = Settings.instance().getFilterSelectedMonth();
        if (StringUtils.isNotBlank(selectedMonth)) {
            Date month = new Date();
            try {
                month.setMonth(Integer.parseInt(selectedMonth) - 1);
                filter.setMonth(month);
            } catch (NumberFormatException e) {
                log.error(e, e);
            }
        }
        
        final String selectedYear = Settings.instance().getFilterSelectedYear();
        if (StringUtils.isNotBlank(selectedYear)) {
            try {
                Calendar cal = GregorianCalendar.getInstance();
                cal.set(Calendar.YEAR, Integer.parseInt(selectedYear));
                filter.setYear(cal.getTime());
            } catch (NumberFormatException e) {
                log.error(e, e);
            }
        }
        
        return filter;
    }

}

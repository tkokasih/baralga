package org.remast.baralga.gui.model;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Observable;

import javax.swing.SwingUtilities;

import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.joda.time.DateTime;
import org.remast.baralga.gui.BaralgaMain;
import org.remast.baralga.gui.events.BaralgaEvent;
import org.remast.baralga.gui.lists.DayFilterList;
import org.remast.baralga.gui.lists.MonthFilterList;
import org.remast.baralga.gui.lists.ProjectFilterList;
import org.remast.baralga.gui.lists.WeekOfYearFilterList;
import org.remast.baralga.gui.lists.YearFilterList;
import org.remast.baralga.gui.model.edit.EditStack;
import org.remast.baralga.gui.model.io.DataBackup;
import org.remast.baralga.gui.model.report.HoursByDayReport;
import org.remast.baralga.gui.model.report.HoursByMonthReport;
import org.remast.baralga.gui.model.report.HoursByProjectReport;
import org.remast.baralga.gui.model.report.HoursByWeekReport;
import org.remast.baralga.gui.model.report.ObservingAccumulatedActivitiesReport;
import org.remast.baralga.gui.settings.UserSettings;
import org.remast.baralga.model.ProTrack;
import org.remast.baralga.model.Project;
import org.remast.baralga.model.ProjectActivity;
import org.remast.baralga.model.filter.Filter;
import org.remast.baralga.model.io.ProTrackWriter;
import org.remast.util.DateUtils;
import org.remast.util.TextResourceBundle;

import ca.odell.glazedlists.BasicEventList;
import ca.odell.glazedlists.SortedList;

/**
 * The model of the Baralga application. This is the model capturing both the state
 * of the application as well as the application logic.
 * For further information on the pattern see <a href="http://www.martinfowler.com/eaaDev/PresentationModel.html">presentation model</a>.
 * @author remast
 */
public class PresentationModel extends Observable {

    /** The logger. */
    @SuppressWarnings("unused")
    private static final Log log = LogFactory.getLog(PresentationModel.class);

    /** The bundle for internationalized texts. */
    private static final TextResourceBundle textBundle = TextResourceBundle.getBundle(BaralgaMain.class);

    /** The list of active projects. */
    private final SortedList<Project> projectList;

    /** The list of all projects (both active and inactive). */
    private final SortedList<Project> allProjectsList;

    /** The list of project activities. */
    private final SortedList<ProjectActivity> activitiesList;

    /** The currently selected project. */
    private Project selectedProject;

    /** The description of the current activity. */
    private String description;

    /** Flag indicating whether selected project is active or not. */
    private boolean active;

    /** Flag indicating whether data has been saved after last change.
     * 
     * Also - because it's volatile - acts as a memory barrier, so
     * different threads can see the changes.
     */
    private volatile boolean dirty = false;

    /** Start date of activity. */
    private DateTime start;

    /** Stop date of activity. */
    private DateTime stop;

    /** The data file that is presented by this model. */
    private ProTrack data;

    /** Current activity filter. */
    private Filter filter;

    /** The stack of edit actions (for undo and redo). */
    private EditStack editStack;

	private BaralgaDAO baralgaDAO;

    /**
     * Creates a new model.
     */
    public PresentationModel() {
        this.data = new ProTrack();
        this.baralgaDAO = new BaralgaDAO();
        this.projectList = new SortedList<Project>(new BasicEventList<Project>());
        this.allProjectsList = new SortedList<Project>(new BasicEventList<Project>());
        this.activitiesList = new SortedList<ProjectActivity>(new BasicEventList<ProjectActivity>());

        initialize();
    }

    /**
     * Initializes the model.
     */
    private void initialize() {
        this.active = this.data.isActive();
        this.start = this.data.getStart();
        this.selectedProject = this.data.getActiveProject();

        this.projectList.clear();
        for (Project project : this.baralgaDAO.getActiveProjects()) {
            if (project.isActive()) {
                this.projectList.add(project);        
            }
        }

        this.allProjectsList.clear();
        this.allProjectsList.addAll(this.baralgaDAO.getAllProjects());

        this.activitiesList.clear();

        // Set restored filter from settings
        setFilter(UserSettings.instance().restoreFromSettings(), this);

        // b) restore project (can be done here only as we need to search all projects)
        final Long selectedProjectId = UserSettings.instance().getFilterSelectedProjectId();
        if (selectedProjectId != null) {
            filter.setProject(
                    this.data.findProjectById(selectedProjectId.longValue())
            );
        }
        applyFilter();

        this.description = UserSettings.instance().getLastDescription();

        // If there is a active project that has been started on another day,
        // we end it here.
        if (active && !org.apache.commons.lang.time.DateUtils.isSameDay(start.toDate(), DateUtils.getNow())) {
            try {
                stop();
            } catch (ProjectActivityStateException e) {
                // Ignore
            }
        }

        // Edit stack
        if (editStack == null) {
            editStack = new EditStack(this);
            this.addObserver(editStack);
        }

    }

    private void applyFilter() {
        this.activitiesList.clear();

        if (this.filter == null) {
            this.activitiesList.addAll(this.baralgaDAO.getActivities());
        } else {
            this.activitiesList.addAll(this.filter.applyFilters(this.baralgaDAO.getActivities()));
        }
    }

    /**
     * Add the given project.
     * @param project the project to add
     * @param source the source of the edit activity
     */
    public final void addProject(final Project project, final Object source) {
//        getData().add(project);
        this.baralgaDAO.addProject(project);
        
        this.projectList.add(project);
        this.allProjectsList.add(project);

        // Mark data as dirty
        this.dirty = true;

        final BaralgaEvent event = new BaralgaEvent(BaralgaEvent.PROJECT_ADDED, source);
        event.setData(project);

        notify(event);
    }

    /**
     * Remove the given project.
     * @param project the project to remove
     * @param source the source of the edit activity
     */
    public final void removeProject(final Project project, final Object source) {
//        getData().remove(project);
        this.baralgaDAO.remove(project);
        
        this.projectList.remove(project);
        this.allProjectsList.remove(project);

        // Mark data as dirty
        this.dirty = true;

        final BaralgaEvent event = new BaralgaEvent(BaralgaEvent.PROJECT_REMOVED, source);
        event.setData(project);

        notify(event);
    }

    /**
     * Start a project activity at the given time.<br/>
     * <em>This method is meant for unit testing only!!</em>
     * @throws ProjectActivityStateException if there is already a running project
     *   or if no project is selected, currently
     */
    public final void start(final DateTime startTime) throws ProjectActivityStateException {
        if (getSelectedProject() == null) {
            throw new ProjectActivityStateException(textBundle.textFor("PresentationModel.NoActiveProjectSelectedError")); //$NON-NLS-1$
        }

        if (isActive()) {
            throw new ProjectActivityStateException("There is already an activity running"); // TODO L10N
        }

        // Mark as active
        setActive(true);

        // Mark data as dirty
        this.dirty = true;

        // Set start time to now if null
        DateTime start;
        if (startTime == null) {
            start = DateUtils.getNowAsDateTime();
        } else {
            start = startTime;
        }

        setStart(start);
//        getData().start(start);
        this.baralgaDAO.start(start);

        // Fire start event
        final BaralgaEvent event = new BaralgaEvent(BaralgaEvent.PROJECT_ACTIVITY_STARTED);
        notify(event);
    }

    /**
     * Start a project activity.
     * @throws ProjectActivityStateException if there is already a running project
     */
    public final void start() throws ProjectActivityStateException {
        start(DateUtils.getNowAsDateTime());
    }

    /**
     * Helper method to notify all observers of an event.
     * @param event the event to forward to the observers
     */
    private void notify(final BaralgaEvent event) {

        final Runnable notifyRunner = new Runnable() {

            @Override
            public void run() {
                setChanged();
                notifyObservers(event);
            }
        };
        SwingUtilities.invokeLater(notifyRunner);
    }

    /**
     * Fires an event that a projects property has changed.
     * @param changedProject the project that's changed
     * @param propertyChangeEvent the event to fire
     */
    public void fireProjectChangedEvent(final Project changedProject, final PropertyChangeEvent propertyChangeEvent) {
        final BaralgaEvent event = new BaralgaEvent(BaralgaEvent.PROJECT_CHANGED);
        event.setData(changedProject);
        event.setPropertyChangeEvent(propertyChangeEvent);
        
        this.baralgaDAO.updateProject(changedProject);

        // Mark data as dirty
        this.dirty = true;

        notify(event);

        if (propertyChangeEvent.getPropertyName().equals(Project.PROPERTY_ACTIVE)) {
            if (changedProject.isActive()) {
                this.projectList.add(changedProject);
            } else {
                this.projectList.remove(changedProject);
            }
        }
    }

    /**
     * Fires an event that a project activity's property has changed.
     * @param changedActivity the project activity that's changed
     * @param propertyChangeEvent the event to fire
     */
    public void fireProjectActivityChangedEvent(final ProjectActivity changedActivity, final PropertyChangeEvent propertyChangeEvent) {
        final BaralgaEvent event = new BaralgaEvent(BaralgaEvent.PROJECT_ACTIVITY_CHANGED);
        event.setData(changedActivity);
        event.setPropertyChangeEvent(propertyChangeEvent);

        this.baralgaDAO.updateActivity(changedActivity);

        // Check whether the activity has been filtered before and whether it is filtered now (after the change).
        final boolean matchesFilter = filter != null && filter.matchesCriteria(changedActivity);
        if (activitiesList.contains(changedActivity)) {
            // Did match before but doesn't now.
            if (!matchesFilter) {
                activitiesList.remove(changedActivity);
            }
        } else {
            // Didn't match before but does now.
            if (matchesFilter) {
                activitiesList.add(changedActivity);
            }            
        }

        // Mark data as dirty
        this.dirty = true;

        notify(event);
    }

    /**
     * Stop a project activity.
     * @throws ProjectActivityStateException if there is no running project
     * @see #stop(boolean)
     */
    public final void stop() throws ProjectActivityStateException {
        // Stop with notifying observers.
        stop(true);
    }

    /**
     * Stop a project activity.<br/>
     * @throws ProjectActivityStateException if there is no running project
     */
    public final void stop(final boolean notifyObservers) throws ProjectActivityStateException {
        if (!isActive()) {
            throw new ProjectActivityStateException(textBundle.textFor("PresentationModel.NoActiveProjectError")); //$NON-NLS-1$
        }

        final DateTime now = DateUtils.getNowAsDateTime();

        BaralgaEvent eventOnEndDay = null;
        DateTime stop2 = null;

        // If start is on a different day from now end the activity at 0:00 one day after start.
        // Also make a new activity from 0:00 the next day until the stop time of the next day.
        if (!org.apache.commons.lang.time.DateUtils.isSameDay(start.toDate(), now.toDate())) {
            DateTime dt = new DateTime(start);
            dt = dt.plusDays(1);

            stop = dt.toDateMidnight().toDateTime();

            stop2 = DateUtils.getNowAsDateTime();
            final DateTime start2 = stop;

            final ProjectActivity activityOnEndDay = new ProjectActivity(start2, stop2,
                    getSelectedProject(), this.description);
//            getData().addActivity(activityOnEndDay);
            this.baralgaDAO.addActivity(activityOnEndDay);
            this.activitiesList.add(activityOnEndDay);

            // Create Event for Project Activity
            eventOnEndDay  = new BaralgaEvent(BaralgaEvent.PROJECT_ACTIVITY_ADDED);
            final Collection<ProjectActivity> activitiesOnEndDay = new ArrayList<ProjectActivity>(1);
            activitiesOnEndDay.add(activityOnEndDay);            
            eventOnEndDay.setData(activitiesOnEndDay);
        } else {
            stop = now;
        }

        final ProjectActivity activityOnStartDay = new ProjectActivity(start, stop,
                getSelectedProject(), this.description);
//        getData().addActivity(activityOnStartDay);
        this.baralgaDAO.addActivity(activityOnStartDay);
        this.activitiesList.add(activityOnStartDay);

        // Clear old activity
        description = StringUtils.EMPTY;
        UserSettings.instance().setLastDescription(StringUtils.EMPTY);
        setActive(false);
//        getData().stop();
        this.baralgaDAO.stop();
        start = null;

        // Mark data as dirty
        this.dirty = true;

        if (notifyObservers) {
            // Create Event for Project Activity
            BaralgaEvent event  = new BaralgaEvent(BaralgaEvent.PROJECT_ACTIVITY_ADDED);
            final Collection<ProjectActivity> activitiesOnStartDay = new ArrayList<ProjectActivity>(1);
            activitiesOnStartDay.add(activityOnStartDay);   
            event.setData(activitiesOnStartDay);
            notify(event);

            if (eventOnEndDay != null)  {
                notify(eventOnEndDay);
                stop = stop2;
            }

            // Create Stop Event
            event = new BaralgaEvent(BaralgaEvent.PROJECT_ACTIVITY_STOPPED);
            notify(event);
        }
    }

    /**
     * Changes to the given project.
     * @param activeProject the new active project
     */
    public final void changeProject(final Project activeProject) {
        // If there's no change we're done.
        if (ObjectUtils.equals(getSelectedProject(), activeProject)) {
            return;
        }

        // Store previous project
        final Project previousProject = getSelectedProject();

        // Set selected project to new project
        this.selectedProject = activeProject;

        // Set active project to new project
        this.data.setActiveProject(activeProject);

        // Mark data as dirty
        this.dirty = true;

        final DateTime now = DateUtils.getNowAsDateTime();

        // If a project is currently running we create a new project activity.
        if (isActive()) {
            // 1. Stop the running project.
            setStop(now);

            // 2. Track recorded project activity.
            final ProjectActivity activity = new ProjectActivity(start, stop, previousProject, description);

//            getData().addActivity(activity);
            this.baralgaDAO.addActivity(activity);
            this.activitiesList.add(activity);

            // Clear description
            description = StringUtils.EMPTY;
            UserSettings.instance().setLastDescription(StringUtils.EMPTY);

            // 3. Broadcast project activity event.
            final BaralgaEvent event = new BaralgaEvent(BaralgaEvent.PROJECT_ACTIVITY_ADDED);
            final Collection<ProjectActivity> activities = new ArrayList<ProjectActivity>(1);
            activities.add(activity);
            event.setData(activities);
            
            notify(event);

            // Set start time to now.
            // :INFO: No need to clone instance because DateTime is immutable 
            setStart(now);
        }

        // Fire project changed event
        final BaralgaEvent event = new BaralgaEvent(BaralgaEvent.PROJECT_CHANGED);
        event.setData(activeProject);
        notify(event);
    }

    /**
     * Save the model.
     * @throws Exception on error during saving
     */
    public final void save() throws Exception {
        // If there are no changes there's nothing to do.
        if (!dirty)  {
            return;
        }

        // Save data to disk.
        final ProTrackWriter writer = new ProTrackWriter(data);

        final File proTrackFile = new File(UserSettings.instance().getDataFileLocation());
        DataBackup.createBackup(proTrackFile);

        writer.write(proTrackFile);        
    }

    /**
     * Add a new activity to the model.
     * @param activity the activity to add
     */
    public final void addActivity(final ProjectActivity activity, final Object source) {
        final Collection<ProjectActivity> activities = new ArrayList<ProjectActivity>(1);
        activities.add(activity);

        this.addActivities(activities, source);
    }
    
    public final void addActivities(final Collection<ProjectActivity> activities, final Object source) {
//        getData().addActivities(activities);
        this.baralgaDAO.addActivities(activities);

        if (this.filter == null) {
            this.getActivitiesList().addAll(activities);
        } else {
            this.getActivitiesList().addAll(
                    this.filter.applyFilters(activities)
            );
        }
        
        // Mark data as dirty
        this.dirty = true;

        // Fire event
        final BaralgaEvent event = new BaralgaEvent(BaralgaEvent.PROJECT_ACTIVITY_ADDED, source);
        event.setData(activities);
        notify(event);
    }    

    /**
     * Remove an activity from the model.
     * @param activity the activity to remove
     */
    public final void removeActivity(final ProjectActivity activity, final Object source) {
        final Collection<ProjectActivity> activities = new ArrayList<ProjectActivity>(1);
        activities.add(activity);

        this.removeActivities(activities, source);
    }

    public final void removeActivities(final Collection<ProjectActivity> activities, final Object source) {
//        getData().removeActivities(activities);
        this.baralgaDAO.removeActivities(activities);
        this.getActivitiesList().removeAll(activities);

        // Mark data as dirty
        this.dirty = true;

        // Fire event
        final BaralgaEvent event = new BaralgaEvent(
                BaralgaEvent.PROJECT_ACTIVITY_REMOVED, 
                source
        );
        
        event.setData(activities);
        notify(event);
    }

    /**
     * Getter for the list of active projects.
     * @return the list with all active projects
     */
    public final SortedList<Project> getProjectList() {
        return projectList;
    }

    /**
     * Getter for the list of projects.
     * @return the list with all projects
     */
    public final SortedList<Project> getAllProjectsList() {
        return allProjectsList;
    }

    /**
     * Getter for the list of project activities.
     * @return the list with all project activities
     */
    public final SortedList<ProjectActivity> getActivitiesList() {
        return activitiesList;
    }

    public final ProjectFilterList getProjectFilterList() {
        return new ProjectFilterList(this);
    }

    /**
     * Get all years in which there are project activities.
     * @return List of years with activities as String.
     */
    public final YearFilterList getYearFilterList() {
        return new YearFilterList(this);
    }

    /**
     * Get all months in which there are project activities.
     * @return List of months with activities as String.
     */
    public final MonthFilterList getMonthFilterList() {
        return new MonthFilterList(this);
    }

    /**
     * Get all days of the current week.
     * @return List of days.
     */
    public final DayFilterList getDayFilterList() {
        return new DayFilterList(this);
    }

    /**
     * Get all weeks in which there are project activities.
     * @return List of weeks with activities as String.
     */
    public final WeekOfYearFilterList getWeekFilterList() {
        return new WeekOfYearFilterList(this);
    }

    /**
     * Getter for the ObservingAccumulatedActivitiesReport.
     * @return the ObservingAccumulatedActivitiesReport to get
     */
    public final ObservingAccumulatedActivitiesReport getFilteredReport() {
        return new ObservingAccumulatedActivitiesReport(this);
    }

    /**
     * Getter for the HoursByWeekReport.
     * @return the HoursByWeekReport to get
     */
    public final HoursByWeekReport getHoursByWeekReport() {
        return new HoursByWeekReport(this);
    }

    /**
     * Getter for the HoursByMonthReport.
     * @return the HoursByMonthReport to get
     */
    public final HoursByMonthReport getHoursByMonthReport() {
        return new HoursByMonthReport(this);
    }

    /**
     * Getter for the HoursByDayReport.
     * @return the HoursByDayReport to get
     */
    public final HoursByDayReport getHoursByDayReport() {
        return new HoursByDayReport(this);
    }

    /**
     * Getter for the HoursByProjectReport.
     * @return the HoursByProjectReport to get
     */
    public final HoursByProjectReport getHoursByProjectReport() {
        return new HoursByProjectReport(this);
    }

    /**
     * Gets the start of the current activity.
     * @return the start
     */
    public final DateTime getStart() {
        return start;
    }

    /**
     * Sets the start of a new activity.
     * @param start the start to set
     */
    public final void setStart(final DateTime start) {
        this.start = start;
        this.data.setStartTime(start);

        // Fire event
        final BaralgaEvent event = new BaralgaEvent(BaralgaEvent.START_CHANGED, this);
        event.setData(start);

        notify(event);
    }

    /**
     * Getter for the stop time.
     * @return the stop
     */
    public final DateTime getStop() {
        return stop;
    }

    /**
     * Setter for the stop time.
     * @param stop the stop to set
     */
    private void setStop(final DateTime stop) {
        this.stop = stop;
    }

    /**
     * Checks whether a project activity is currently running.
     * @return the active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * @param active the active to set
     */
    public void setActive(final boolean active) {
        this.active = active;
    }

    /**
     * @return the activeProject
     */
    public Project getSelectedProject() {
        return selectedProject;
    }

    /**
     * @return the data
     */
    public ProTrack getData() {
        return data;
    }

    /**
     * @param data the data to set
     */
    public void setData(final ProTrack data) {
//        if (ObjectUtils.equals(this.data, data)) {
//            return;
//        }
//
//        this.data = data;
//
//        initialize();
//
//        // Fire event for changed data
//        final BaralgaEvent eventDataChanged = new BaralgaEvent(BaralgaEvent.DATA_CHANGED, this);
//        notify(eventDataChanged);
//
//        // Fire event for changed project
//        final BaralgaEvent projectChangedEvent = new BaralgaEvent(BaralgaEvent.PROJECT_CHANGED, this);
//        final Project project = this.data.getActiveProject();
//        projectChangedEvent.setData(project);
//        notify(projectChangedEvent);    
    }

    /**
     * @return the filter
     */
    public Filter getFilter() {
        return filter;
    }

    /**
     * @param filter the filter to set
     * @param source the source of the new filter
     */
    public void setFilter(final Filter filter, final Object source) {
        if (ObjectUtils.equals(this.filter, filter)) {
            return;
        }

        // Store filter
        this.filter = filter;

        applyFilter();

        // Fire event
        final BaralgaEvent event = new BaralgaEvent(BaralgaEvent.FILTER_CHANGED, source);
        event.setData(filter);

        notify(event);
    }

    /**
     * Getter for the description.
     * @return the description to get
     */
    public final String getDescription() {
        return description;
    }

    /**
     * Setter for the description.
     * @param description the description to set
     */
    public final void setDescription(final String description) {
        this.description = description;
    }

    /**
     * @return the editStack
     */
    public final EditStack getEditStack() {
        return editStack;
    }

    /**
     * @return the dirty
     */
    public final boolean isDirty() {
        return dirty;
    }

    /**
     * @param dirty the dirty to set
     */
    public final void setDirty(final boolean dirty) {
        this.dirty = dirty;
    }

}

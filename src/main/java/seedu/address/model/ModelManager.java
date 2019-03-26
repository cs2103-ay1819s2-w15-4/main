package seedu.address.model;

import static java.util.Objects.requireNonNull;
import static seedu.address.commons.util.CollectionUtil.requireAllNonNull;

import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Logger;

import javafx.beans.property.ReadOnlyProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import seedu.address.commons.core.GuiSettings;
import seedu.address.commons.core.LogsCenter;
import seedu.address.model.calendar.TempClass;
import seedu.address.model.person.Person;
import seedu.address.model.person.exceptions.PersonNotFoundException;

/**
 * Represents the in-memory model of the address book data.
 */
public class ModelManager implements Model {
    private static final Logger logger = LogsCenter.getLogger(ModelManager.class);

    private final VersionedPersonnelDatabase versionedPersonnelDatabase;
    private final UserPrefs userPrefs;
    private final FilteredList<Person> filteredPersons;
    private final DutyCalendar dutyCalendar;
    private final SimpleObjectProperty<Person> selectedPerson = new SimpleObjectProperty<>();

    /**
     * Initializes a ModelManager with the given personnelDatabase, dutyCalendar and userPrefs.
     */
    public ModelManager(ReadOnlyPersonnelDatabase personnelDatabase, ReadOnlyUserPrefs userPrefs) {

        super();
        requireAllNonNull(personnelDatabase, userPrefs);

        logger.fine("Initializing with personnel database: " + personnelDatabase + " and user prefs " + userPrefs);

        versionedPersonnelDatabase = new VersionedPersonnelDatabase(personnelDatabase);
        this.userPrefs = new UserPrefs(userPrefs);
        filteredPersons = new FilteredList<>(versionedPersonnelDatabase.getPersonList());
        filteredPersons.addListener(this::ensureSelectedPersonIsValid);
        /*
        Note: just a test method for UI, remove when done
        */

        dutyCalendar = versionedPersonnelDatabase.getDutyCalendar();

    }

    public ModelManager() {
        this(new PersonnelDatabase(), new UserPrefs());
    }

    //=========== UserPrefs ==================================================================================

    @Override
    public void setUserPrefs(ReadOnlyUserPrefs userPrefs) {
        requireNonNull(userPrefs);
        this.userPrefs.resetData(userPrefs);
    }

    @Override
    public ReadOnlyUserPrefs getUserPrefs() {
        return userPrefs;
    }

    @Override
    public GuiSettings getGuiSettings() {
        return userPrefs.getGuiSettings();
    }

    @Override
    public void setGuiSettings(GuiSettings guiSettings) {
        requireNonNull(guiSettings);
        userPrefs.setGuiSettings(guiSettings);
    }

    @Override
    public Path getPersonnelDatabaseFilePath() {
        return userPrefs.getPersonnelDatabaseFilePath();
    }

    @Override
    public void setPersonnelDatabaseFilePath(Path personnelDatabaseFilePath) {
        requireNonNull(personnelDatabaseFilePath);
        userPrefs.setPersonnelDatabaseFilePath(personnelDatabaseFilePath);
    }

    //=========== PersonnelDatabase ================================================================================

    @Override
    public void setPersonnelDatabase(ReadOnlyPersonnelDatabase personnelDatabase) {
        versionedPersonnelDatabase.resetData(personnelDatabase);
    }

    @Override
    public ReadOnlyPersonnelDatabase getPersonnelDatabase() {
        return versionedPersonnelDatabase;
    }

    @Override
    public void sortPersonnelDatabase() {
        versionedPersonnelDatabase.sort();
    }

    @Override
    public boolean hasPerson(Person person) {
        requireNonNull(person);
        return versionedPersonnelDatabase.hasPerson(person);
    }

    @Override
    public void deletePerson(Person target) {
        versionedPersonnelDatabase.removePerson(target);
    }

    @Override
    public void addPerson(Person person) {
        versionedPersonnelDatabase.addPerson(person);
        updateFilteredPersonList(PREDICATE_SHOW_ALL_PERSONS);
    }

    @Override
    public void setPerson(Person target, Person editedPerson) {
        requireAllNonNull(target, editedPerson);

        versionedPersonnelDatabase.setPerson(target, editedPerson);
    }

    //=========== Duty Calendar ================================================================================

    public DutyCalendar getDutyCalendar() {
        return this.dutyCalendar;
    }

    //=========== Filtered Person List Accessors =============================================================

    /**
     * Returns an unmodifiable view of the list of {@code Person} backed by the internal list of
     * {@code versionedAddressBook}
     */
    @Override
    public ObservableList<Person> getFilteredPersonList() {
        return filteredPersons;
    }

    //@Override
    //public ObservableList<Person> getDutyForDates() {return dutyForDates; }

    @Override
    public void updateFilteredPersonList(Predicate<Person> predicate) {
        requireNonNull(predicate);
        filteredPersons.setPredicate(predicate);
    }


    //=========== Undo/Redo =================================================================================

    @Override
    public boolean canUndoPersonnelDatabase() {
        return versionedPersonnelDatabase.canUndo();
    }

    @Override
    public boolean canRedoPersonnelDatabase() {
        return versionedPersonnelDatabase.canRedo();
    }

    @Override
    public void undoPersonnelDatabase() {
        versionedPersonnelDatabase.undo();
    }

    @Override
    public void redoPersonnelDatabase() {
        versionedPersonnelDatabase.redo();
    }

    @Override
    public void commitPersonnelDatabase() {
        versionedPersonnelDatabase.commit();
    }

    //=========== Selected person ===========================================================================

    @Override
    public ReadOnlyProperty<Person> selectedPersonProperty() {
        return selectedPerson;
    }

    @Override
    public Person getSelectedPerson() {
        return selectedPerson.getValue();
    }

    @Override
    public void setSelectedPerson(Person person) {
        if (person != null && !filteredPersons.contains(person)) {
            throw new PersonNotFoundException();
        }
        selectedPerson.setValue(person);
    }

    /**
     * Ensures {@code selectedPerson} is a valid person in {@code filteredPersons}.
     */
    private void ensureSelectedPersonIsValid(ListChangeListener.Change<? extends Person> change) {
        while (change.next()) {
            if (selectedPerson.getValue() == null) {
                // null is always a valid selected person, so we do not need to check that it is valid anymore.
                return;
            }

            boolean wasSelectedPersonReplaced = change.wasReplaced() && change.getAddedSize() == change.getRemovedSize()
                    && change.getRemoved().contains(selectedPerson.getValue());
            if (wasSelectedPersonReplaced) {
                // Update selectedPerson to its new value.
                int index = change.getRemoved().indexOf(selectedPerson.getValue());
                selectedPerson.setValue(change.getAddedSubList().get(index));
                continue;
            }

            boolean wasSelectedPersonRemoved = change.getRemoved().stream()
                    .anyMatch(removedPerson -> selectedPerson.getValue().isSamePerson(removedPerson));
            if (wasSelectedPersonRemoved) {
                // Select the person that came before it in the list,
                // or clear the selection if there is no such person.
                selectedPerson.setValue(change.getFrom() > 0 ? change.getList().get(change.getFrom() - 1) : null);
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        // short circuit if same object
        if (obj == this) {
            return true;
        }

        // instanceof handles nulls
        if (!(obj instanceof ModelManager)) {
            return false;
        }

        // state check
        ModelManager other = (ModelManager) obj;
        return versionedPersonnelDatabase.equals(other.versionedPersonnelDatabase)
                && userPrefs.equals(other.userPrefs)
                && filteredPersons.equals(other.filteredPersons)
                && Objects.equals(selectedPerson.get(), other.selectedPerson.get());
    }

}

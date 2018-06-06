/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account;

import android.app.*;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import com.codetroopers.betterpickers.calendardatepicker.CalendarDatePickerDialogFragment;
import com.codetroopers.betterpickers.calendardatepicker.MonthAdapter.CalendarDay;
import com.yalantis.ucrop.UCrop;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.ServerStoredDetails.*;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.ServiceUtils;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.account.settings.AccountPreferencesActivity;
import org.atalk.android.gui.util.*;
import org.atalk.android.gui.util.event.EventListener;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.util.SoftKeyboard;
import org.atalk.util.StringUtils;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.osgi.framework.BundleContext;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Activity allows user to set presence status, status message, change the avatar
 * and all the vCard-temp information for the {@link #mAccount}.
 *
 * The main panel that allows users to view and edit their account information.
 * Different instances of this class are created for every registered
 * <tt>ProtocolProviderService</tt>.
 * Currently, supported account details are first/middle/last names, nickname,
 * street/city/region/country address, postal code, birth date, gender,
 * organization name, job title, about me, home/work email, home/work phone.
 *
 *
 * The {@link #mAccount} is retrieved from the {@link Intent} extra by it's
 * {@link AccountID#getAccountUniqueID()}
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AccountInfoPresenceActivity extends OSGiActivity
        implements EventListener<AccountEvent>, AdapterView.OnItemSelectedListener,
        SoftKeyboard.SoftKeyboardChanged, CalendarDatePickerDialogFragment.OnDateSetListener
{
    /**
     * The logger used by this class
     */
    static final private Logger logger = Logger.getLogger(AccountInfoPresenceActivity.class);

    /**
     * Calender Date Picker parameters
     */
    private CalendarDatePickerDialogFragment calendarDatePicker;

    private static final String FRAG_TAG_DATE_PICKER = "fragment_date_picker_name";

    private static final CalendarDay DEFAULT_START_DATE = new CalendarDay(1900, Calendar.JANUARY, 1);

    private static final String AVATAR_ICON_REMOVE = "Remove Picture";

    // avatar default image size
    private static final int AVATAR_PREFERRED_SIZE = 64;
    private static final int CROP_MAX_SIZE = 108;

    /**
     * Intent's extra's key for account ID property of this activity
     */
    static public final String INTENT_ACCOUNT_ID = "account_id";

    /**
     * The id for the "select image from gallery" intent result
     */
    static private final int SELECT_IMAGE = 1;

    /**
     * The account's {@link OperationSetPresence} used to perform presence operations
     */
    private OperationSetPresence accountPresence;

    /**
     * The instance of {@link Account} used for operations on the account
     */
    private Account mAccount;

    /**
     * Flag indicates if there were any uncommitted changes that shall be applied on exit
     */
    private boolean hasChanges = false;

    /**
     * Flag indicates if there were any uncommitted status changes that shall be applied on exit
     */
    private boolean hasStatusChanges = false;

    private boolean firstInit = true;

    /**
     * Mapping between all supported by this plugin <tt>ServerStoredDetails</tt> and their
     * respective <tt>EditText</tt> that are used for modifying the details.
     */
    private final Map<Class<? extends GenericDetail>, EditText> detailToTextField = new HashMap<>();

    /**
     * The <tt>ProtocolProviderService</tt> that this panel is associated with.
     */
    ProtocolProviderService protocolProvider;

    /**
     * The operation set giving access to the server stored account details.
     */
    private OperationSetServerStoredAccountInfo accountInfoOpSet;

    /*
     * imageUrlField contains the link to the image or a command to remove avatar
     */
    private EditText imageUrlField;

    private EditText urlField;
    private EditText aboutMeArea;
    private EditText ageField;
    private EditText birthDateField;

    private EditTextWatcher editTextWatcher;

    private DisplayNameDetail displayNameDetail;
    private FirstNameDetail firstNameDetail;
    private MiddleNameDetail middleNameDetail;
    private LastNameDetail lastNameDetail;
    private NicknameDetail nicknameDetail;
    private URLDetail urlDetail;
    private AddressDetail streetAddressDetail;
    private CityDetail cityDetail;
    private ProvinceDetail regionDetail;
    private PostalCodeDetail postalCodeDetail;
    private CountryDetail countryDetail;
    private PhoneNumberDetail phoneDetail;
    private WorkPhoneDetail workPhoneDetail;
    private MobilePhoneDetail mobilePhoneDetail;
    private EmailAddressDetail emailDetail;
    private WorkEmailAddressDetail workEmailDetail;
    private WorkOrganizationNameDetail organizationDetail;
    private JobTitleDetail jobTitleDetail;
    private AboutMeDetail aboutMeDetail;
    private GenderDetail genderDetail;
    private BirthDateDetail birthDateDetail;

    private ImageView avatarView;
    private ImageDetail avatarDetail;
    private DateFormat dateFormat;

    /**
     * Container for apply and cancel buttons; auto- hide when field text entry is active
     */
    private View mButtonContainer;

    private ImageView mCalenderButton;
    private SoftKeyboard softKeyboard;
    private ProgressDialog progressDialog;
    boolean isRegistered;

    @Override
    protected void onCreate(android.os.Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // Set the main layout
        setContentView(R.layout.account_info_presence_status);
        mButtonContainer = findViewById(R.id.button_Container);

        avatarView = findViewById(R.id.accountAvatar);
        registerForContextMenu(avatarView);
        avatarView.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                openContextMenu(avatarView);
            }
        });

        // Get account ID from intent extras; and find account for given account ID
        String accountIDStr = getIntent().getStringExtra(INTENT_ACCOUNT_ID);
        AccountID accountID = AccountUtils.getAccountIDForUID(accountIDStr);

        if (accountID == null) {
            logger.error("No account found for: " + accountIDStr);
            finish();
            return;
        }

        mAccount = new Account(accountID, AndroidGUIActivator.bundleContext, this);
        mAccount.addAccountEventListener(this);
        protocolProvider = mAccount.getProtocolProvider();

        editTextWatcher = new EditTextWatcher();
        initPresenceStatus();
        initSoftKeyboard();

        accountInfoOpSet = protocolProvider.getOperationSet(OperationSetServerStoredAccountInfo.class);
        if (accountInfoOpSet != null) {
            isRegistered = protocolProvider.isRegistered();

            initSummaryPanel();
            if (!isRegistered) {
                setTextEditState(false);
                Toast.makeText(this, R.string.plugin_accountinfo_NO_REGISTERED_MESSAGE, Toast.LENGTH_LONG).show();
            }
            else {
                loadDetails();
            }
        }
    }

    @Override
    protected void stop(BundleContext bundleContext)
            throws Exception
    {
        super.stop(bundleContext);
        softKeyboard.unRegisterSoftKeyboardCallback();

        if (progressDialog != null)
            progressDialog.dismiss();
    }

    @Override
    public void onBackPressed()
    {
        if (!hasChanges && !hasStatusChanges) {
            super.onBackPressed();
        }
        else {
            checkUnsavedChanges();
        }
    }

    /**
     * Create and initialize the view with actual values
     */
    private void initPresenceStatus()
    {
        this.accountPresence = mAccount.getPresenceOpSet();

        // Check for presence support
        if (accountPresence == null) {
            Toast.makeText(this, getString(R.string.service_gui_PRESENCE_NOT_SUPPORTED,
                    mAccount.getAccountName()), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        firstInit = true;
        // Account properties
        String title = mAccount.getAccountName();
        ActionBarUtil.setTitle(this, title);

        // Create spinner with status list
        Spinner statusSpinner = findViewById(R.id.presenceStatusSpinner);

        // Create list adapter
        Iterator<PresenceStatus> statusIter = accountPresence.getSupportedStatusSet();
        StatusListAdapter statusAdapter = new StatusListAdapter(statusIter);
        statusSpinner.setAdapter(statusAdapter);

        // Selects current status
        PresenceStatus presenceStatus = accountPresence.getPresenceStatus();
        ActionBarUtil.setStatus(this, presenceStatus.getStatusIcon());

        statusSpinner.setSelection(statusAdapter.getPositionForItem(presenceStatus));
        statusSpinner.setOnItemSelectedListener(this);

        // Sets current status message
        EditText statusMessageEdit = findViewById(R.id.statusMessage);
        statusMessageEdit.setText(accountPresence.getCurrentStatusMessage());

        // Watch the text for any changes
        statusMessageEdit.addTextChangedListener(editTextWatcher);
    }

    /**
     * Initialized the main panel that contains all <tt>ServerStoredDetails</tt> and update
     * mapping between supported <tt>ServerStoredDetails</tt> and their respective
     * <tt>EditText</tt> that are used for modifying the details.
     */
    private void initSummaryPanel()
    {
        imageUrlField = findViewById(R.id.ai_ImageUrl);
        detailToTextField.put(ImageDetail.class, imageUrlField);

        EditText displayNameField = findViewById(R.id.ai_DisplayNameField);
        View displayNameContainer = findViewById(R.id.ai_DisplayName_Container);
        displayNameContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(DisplayNameDetail.class)) {
            displayNameContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(DisplayNameDetail.class, displayNameField);
        }

        EditText firstNameField = findViewById(R.id.ai_FirstNameField);
        detailToTextField.put(FirstNameDetail.class, firstNameField);

        EditText middleNameField = findViewById(R.id.ai_MiddleNameField);
        detailToTextField.put(MiddleNameDetail.class, middleNameField);

        EditText lastNameField = findViewById(R.id.ai_LastNameField);
        detailToTextField.put(LastNameDetail.class, lastNameField);

        EditText nicknameField = findViewById(R.id.ai_NickNameField);
        View nickNameContainer = findViewById(R.id.ai_NickName_Container);
        nickNameContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(NicknameDetail.class)) {
            nickNameContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(NicknameDetail.class, nicknameField);
        }

        urlField = findViewById(R.id.ai_URLField);
        View urlContainer = findViewById(R.id.ai_URL_Container);
        urlContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(URLDetail.class)) {
            urlContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(URLDetail.class, urlField);
        }

        EditText genderField = findViewById(R.id.ai_GenderField);
        View genderContainer = findViewById(R.id.ai_Gender_Container);
        genderContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(GenderDetail.class)) {
            genderContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(GenderDetail.class, genderField);
        }

        birthDateField = findViewById(R.id.ai_BirthDateField);
        detailToTextField.put(BirthDateDetail.class, birthDateField);
        birthDateField.setEnabled(false);

        ageField = findViewById(R.id.ai_AgeField);
        ageField.setEnabled(false);

        EditText streetAddressField = findViewById(R.id.ai_StreetAddressField);
        View streetAddressContainer = findViewById(R.id.ai_StreetAddress_Container);
        streetAddressContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(AddressDetail.class)) {
            streetAddressContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(AddressDetail.class, streetAddressField);
        }

        EditText cityField = findViewById(R.id.ai_CityField);
        View cityContainer = findViewById(R.id.ai_City_Container);
        cityContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(CityDetail.class)) {
            cityContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(CityDetail.class, cityField);
        }

        EditText regionField = findViewById(R.id.ai_RegionField);
        View regionContainer = findViewById(R.id.ai_Region_Container);
        regionContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(ProvinceDetail.class)) {
            regionContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(ProvinceDetail.class, regionField);
        }

        EditText postalCodeField = findViewById(R.id.ai_PostalCodeField);
        View postalCodeContainer = findViewById(R.id.ai_PostalCode_Container);
        postalCodeContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(PostalCodeDetail.class)) {
            postalCodeContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(PostalCodeDetail.class, postalCodeField);
        }

        EditText countryField = findViewById(R.id.ai_CountryField);
        View countryContainer = findViewById(R.id.ai_Country_Container);
        countryContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(CountryDetail.class)) {
            countryContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(CountryDetail.class, countryField);
        }

        EditText emailField = findViewById(R.id.ai_EMailField);
        detailToTextField.put(EmailAddressDetail.class, emailField);

        EditText workEmailField = findViewById(R.id.ai_WorkEmailField);
        View workEmailContainer = findViewById(R.id.ai_WorkEmail_Container);
        workEmailContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(WorkEmailAddressDetail.class)) {
            workEmailContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(WorkEmailAddressDetail.class, workEmailField);
        }

        EditText phoneField = findViewById(R.id.ai_PhoneField);
        detailToTextField.put(PhoneNumberDetail.class, phoneField);

        EditText workPhoneField = findViewById(R.id.ai_WorkPhoneField);
        View workPhoneContainer = findViewById(R.id.ai_WorkPhone_Container);
        workPhoneContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(WorkPhoneDetail.class)) {
            workPhoneContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(WorkPhoneDetail.class, workPhoneField);
        }

        EditText mobilePhoneField = findViewById(R.id.ai_MobilePhoneField);
        View mobileContainer = findViewById(R.id.ai_MobilePhone_Container);
        mobileContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(MobilePhoneDetail.class)) {
            mobileContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(MobilePhoneDetail.class, mobilePhoneField);
        }

        EditText organizationField = findViewById(R.id.ai_OrganizationNameField);
        View organizationNameContainer = findViewById(R.id.ai_OrganizationName_Container);
        organizationNameContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(WorkOrganizationNameDetail.class)) {
            organizationNameContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(WorkOrganizationNameDetail.class, organizationField);
        }

        EditText jobTitleField = findViewById(R.id.ai_JobTitleField);
        View jobDetailContainer = findViewById(R.id.ai_JobTitle_Container);
        jobDetailContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(JobTitleDetail.class)) {
            jobDetailContainer.setVisibility(View.VISIBLE);
            detailToTextField.put(JobTitleDetail.class, jobTitleField);
        }

        aboutMeArea = findViewById(R.id.ai_AboutMeField);
        View aboutMeContainer = findViewById(R.id.ai_AboutMe_Container);
        aboutMeContainer.setVisibility(View.GONE);
        if (accountInfoOpSet.isDetailClassSupported(AboutMeDetail.class)) {
            aboutMeContainer.setVisibility(View.VISIBLE);
            aboutMeArea.setEnabled(false);

            InputFilter[] filterArray = new InputFilter[1];
            filterArray[0] = new InputFilter.LengthFilter(Integer.valueOf(getString(R.string.plugin_accountinfo_ABOUT_ME_MAX_CHARACTERS)));
            aboutMeArea.setFilters(filterArray);
            aboutMeArea.setBackgroundResource(R.drawable.alpha_blue_01);
            detailToTextField.put(AboutMeDetail.class, aboutMeArea);
        }

        // Setup and initialize birthday calendar basic parameters
        dateFormat = DateFormat.getDateInstance();
        Calendar today = Calendar.getInstance();
        int thisYear = today.get(Calendar.YEAR);
        int thisMonth = today.get(Calendar.MONTH);
        int thisDay = today.get(Calendar.DAY_OF_MONTH);
        CalendarDay TODAY = new CalendarDay(thisYear, thisMonth, thisDay);

        calendarDatePicker = new CalendarDatePickerDialogFragment()
                .setOnDateSetListener(AccountInfoPresenceActivity.this)
                .setFirstDayOfWeek(Calendar.MONDAY)
                .setDateRange(DEFAULT_START_DATE, TODAY)
                .setDoneText("Done")
                .setCancelText("Cancel")
                .setThemeDark();

        mCalenderButton = findViewById(R.id.datePicker);
        mCalenderButton.setEnabled(false);
        mCalenderButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                calendarDatePicker.show(getSupportFragmentManager(), FRAG_TAG_DATE_PICKER);
            }
        });

        Button mApplyButton = findViewById(R.id.button_Apply);
        mApplyButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                if (hasChanges || hasStatusChanges)
                    launchApplyProgressDialog();
                else
                    finish();
            }
        });

        Button mCancelButton = findViewById(R.id.button_Cancel);
        mCancelButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                checkUnsavedChanges();
            }
        });
    }

    private void checkUnsavedChanges()
    {
        if (hasChanges) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.service_gui_UNSAVED_CHANGES_TITLE)
                    .setMessage(R.string.service_gui_UNSAVED_CHANGES)
                    .setPositiveButton(R.string.service_gui_EXIT,
                            new DialogInterface.OnClickListener()
                            {
                                public void onClick(DialogInterface dialog, int which)
                                {
                                    finish();
                                }
                            })
                    .setNegativeButton(R.string.service_gui_NO,
                            new DialogInterface.OnClickListener()
                            {
                                public void onClick(DialogInterface dialog, int which)
                                {
                                }
                            })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
        }
        else {
            finish();
        }
    }

    @Override  // CalendarDatePickerDialogFragment callback
    public void onDateSet(CalendarDatePickerDialogFragment dialog, int year, int monthOfYear,
            int dayOfMonth)
    {
        Calendar mDate = Calendar.getInstance();

        int age = mDate.get(Calendar.YEAR) - year;
        if (mDate.get(Calendar.MONTH) < monthOfYear)
            age--;
        if ((mDate.get(Calendar.MONTH) == monthOfYear)
                && (mDate.get(Calendar.DAY_OF_MONTH) < dayOfMonth))
            age--;

        String ageDetail = Integer.toString(age).trim();
        ageField.setText(ageDetail);

        mDate.set(year, monthOfYear, dayOfMonth);
        birthDateField.setText(dateFormat.format(mDate.getTime()));

        // internal program call is with dialog == null
        hasChanges = (dialog != null);
    }

    private void setTextEditState(boolean editState)
    {
        boolean isEditable;
        for (Class<? extends GenericDetail> editable : detailToTextField.keySet()) {
            EditText field = detailToTextField.get(editable);

            isEditable = editState && accountInfoOpSet.isDetailClassEditable(editable);
            if (editable.equals(BirthDateDetail.class))
                mCalenderButton.setEnabled(isEditable);
            else if (editable.equals(ImageDetail.class))
                avatarView.setEnabled(isEditable);
            else if (field != null) {
                field.setEnabled(isEditable);
                if (isEditable)
                    field.addTextChangedListener(editTextWatcher);
            }
        }
    }

    /**
     * Loads all <tt>ServerStoredDetails</tt> which are currently supported by
     * this plugin. Note that some <tt>OperationSetServerStoredAccountInfo</tt>
     * implementations may support details that are not supported by this plugin.
     * In this case they will not be loaded.
     */
    private void loadDetails()
    {
        if (accountInfoOpSet != null) {
            new DetailsLoadWorker().execute();
        }
    }

    /**
     * Loads details in separate thread.
     */
    private class DetailsLoadWorker extends AsyncTask<Void, Void, Iterator<GenericDetail>>
    {
        @Override
        public void onPreExecute()
        {
        }

        @Override
        protected Iterator<GenericDetail> doInBackground(Void... params)
        {
            return accountInfoOpSet.getAllAvailableDetails();
        }

        /**
         * Called on the event dispatching thread (not on the worker thread)
         * after the <code>construct</code> method has returned.
         */
        @Override
        protected void onPostExecute(Iterator<GenericDetail> result)
        {
            Iterator<GenericDetail> allDetails = null;
            try {
                allDetails = get();
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }

            if (allDetails != null) {
                while (allDetails.hasNext()) {
                    GenericDetail detail = allDetails.next();
                    loadDetail(detail);
                }

                // Setup textFields' editable state and addTextChangedListener if enabled
                boolean isEditable;
                for (Class<? extends GenericDetail> editable : detailToTextField.keySet()) {
                    EditText field = detailToTextField.get(editable);
                    isEditable = accountInfoOpSet.isDetailClassEditable(editable);

                    if (editable.equals(BirthDateDetail.class))
                        mCalenderButton.setEnabled(isEditable);
                    else if (editable.equals(ImageDetail.class))
                        avatarView.setEnabled(isEditable);
                    else {
                        if (field != null) {
                            field.setEnabled(isEditable);
                            if (isEditable)
                                field.addTextChangedListener(editTextWatcher);
                        }
                    }
                }
            }
            // get user avatar via XEP-0084
            getUserAvatarData();
        }
    }

    /**
     * Loads a single <tt>GenericDetail</tt> obtained from the
     * <tt>OperationSetServerStoredAccountInfo</tt> into this plugin.
     *
     * If VcardTemp contains <photo/>, it will be converted to XEP-0084 avatarData &
     * avatarMetadata, and remove it from VCardTemp.
     *
     * @param detail the loaded detail for extraction.
     */
    private void loadDetail(GenericDetail detail)
    {
        if (detail.getClass().equals(AboutMeDetail.class)) {
            aboutMeDetail = (AboutMeDetail) detail;
            aboutMeArea.setText((String) detail.getDetailValue());
            return;
        }

        if (detail instanceof BirthDateDetail) {
            birthDateDetail = (BirthDateDetail) detail;
            Calendar birthDate = (Calendar) birthDateDetail.getDetailValue();
            // default to today if birthDate is null
            if (birthDate == null)
                birthDate = Calendar.getInstance();

            int bYear = birthDate.get(Calendar.YEAR);
            int bMonth = birthDate.get(Calendar.MONTH);
            int bDay = birthDate.get(Calendar.DAY_OF_MONTH);

            // Preset calendarDatePicker date
            calendarDatePicker.setPreselectedDate(bYear, bMonth, bDay);

            // Update BirthDate and Age
            onDateSet(null, bYear, bMonth, bDay);
            return;
        }

        EditText field = detailToTextField.get(detail.getClass());
        if (field != null) {
            if (detail instanceof ImageDetail) {
                avatarDetail = (ImageDetail) detail;
                byte[] avatarImage = avatarDetail.getBytes();
                Bitmap bitmap = BitmapFactory.decodeByteArray(avatarImage, 0, avatarImage.length);
                avatarView.setImageBitmap(bitmap);
            }
            else if (detail instanceof URLDetail) {
                urlDetail = (URLDetail) detail;
                urlField.setText(urlDetail.getURL().toString());
            }
            else {
                Object obj = detail.getDetailValue();
                if (obj instanceof String)
                    field.setText((String) obj);
                else if (obj != null)
                    field.setText(obj.toString());

                if (detail.getClass().equals(DisplayNameDetail.class))
                    displayNameDetail = (DisplayNameDetail) detail;
                else if (detail.getClass().equals(FirstNameDetail.class))
                    firstNameDetail = (FirstNameDetail) detail;
                else if (detail.getClass().equals(MiddleNameDetail.class))
                    middleNameDetail = (MiddleNameDetail) detail;
                else if (detail.getClass().equals(LastNameDetail.class))
                    lastNameDetail = (LastNameDetail) detail;
                else if (detail.getClass().equals(NicknameDetail.class))
                    nicknameDetail = (NicknameDetail) detail;
                else if (detail.getClass().equals(URLDetail.class))
                    urlDetail = (URLDetail) detail;
                else if (detail.getClass().equals(GenderDetail.class))
                    genderDetail = (GenderDetail) detail;
                else if (detail.getClass().equals(AddressDetail.class))
                    streetAddressDetail = (AddressDetail) detail;
                else if (detail.getClass().equals(CityDetail.class))
                    cityDetail = (CityDetail) detail;
                else if (detail.getClass().equals(ProvinceDetail.class))
                    regionDetail = (ProvinceDetail) detail;
                else if (detail.getClass().equals(PostalCodeDetail.class))
                    postalCodeDetail = (PostalCodeDetail) detail;
                else if (detail.getClass().equals(CountryDetail.class))
                    countryDetail = (CountryDetail) detail;
                else if (detail.getClass().equals(PhoneNumberDetail.class))
                    phoneDetail = (PhoneNumberDetail) detail;
                else if (detail.getClass().equals(WorkPhoneDetail.class))
                    workPhoneDetail = (WorkPhoneDetail) detail;
                else if (detail.getClass().equals(MobilePhoneDetail.class))
                    mobilePhoneDetail = (MobilePhoneDetail) detail;
                else if (detail.getClass().equals(EmailAddressDetail.class))
                    emailDetail = (EmailAddressDetail) detail;
                else if (detail.getClass().equals(WorkEmailAddressDetail.class))
                    workEmailDetail = (WorkEmailAddressDetail) detail;
                else if (detail.getClass().equals(
                        WorkOrganizationNameDetail.class))
                    organizationDetail = (WorkOrganizationNameDetail) detail;
                else if (detail.getClass().equals(JobTitleDetail.class))
                    jobTitleDetail = (JobTitleDetail) detail;
                else if (detail.getClass().equals(AboutMeDetail.class))
                    aboutMeDetail = (AboutMeDetail) detail;
            }
        }
    }

    /**
     * Retrieve avatar via XEP-0084 and override vCard <photo/> content if avatarImage not null
     */
    private void getUserAvatarData()
    {
        byte[] avatarImage = AvatarManager.getAvatarImageByJid(mAccount.getJid().asBareJid());
        if (avatarImage != null && avatarImage.length > 0) {
            Bitmap bitmap = BitmapFactory.decodeByteArray(avatarImage, 0, avatarImage.length);
            avatarView.setImageBitmap(bitmap);
        }
        else {
            avatarView.setImageResource(R.drawable.personphoto);
        }
    }

    /**
     * Attempts to upload all <tt>ServerStoredDetails</tt> on the server using
     * <tt>OperationSetServerStoredAccountInfo</tt>
     */
    private void SubmitChangesAction()
    {
        if (!isRegistered || !hasChanges)
            return;

        if (accountInfoOpSet.isDetailClassSupported(ImageDetail.class)) {
            String sCommand = imageUrlField.getText().toString();
            if (!TextUtils.isEmpty(sCommand)) {
                ImageDetail newDetail;

                /*
                 * command to remove avatar photo from vCardTemp. XEP-0084 support will always
                 * init imageUrlField = AVATAR_ICON_REMOVE
                 */
                if (AVATAR_ICON_REMOVE.equals(sCommand)) {
                    newDetail = new ImageDetail("avatar", new byte[0]);
                    changeDetail(avatarDetail, newDetail);
                }
                else {
                    try {
                        Uri imageUri = Uri.parse(sCommand);
                        Bitmap bmp = AndroidImageUtil.scaledBitmapFromContentUri(this,
                                imageUri, AVATAR_PREFERRED_SIZE, AVATAR_PREFERRED_SIZE);

                        // Convert to bytes if not null
                        if (bmp != null) {
                            final byte[] rawImage = AndroidImageUtil.convertToBytes(bmp, 100);

                            newDetail = new ImageDetail("avatar", rawImage);
                            changeDetail(avatarDetail, newDetail);
                        }
                        else
                            showAvatarChangeError();
                    } catch (IOException e) {
                        logger.error(e, e);
                        showAvatarChangeError();
                    }
                }
            }
        }

        if (accountInfoOpSet.isDetailClassSupported(DisplayNameDetail.class)) {
            String text = detailToTextField.get(DisplayNameDetail.class).getText().toString();

            DisplayNameDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new DisplayNameDetail(text);

            if (displayNameDetail != null || newDetail != null)
                changeDetail(displayNameDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(FirstNameDetail.class)) {
            String text = detailToTextField.get(FirstNameDetail.class).getText().toString();

            FirstNameDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new FirstNameDetail(text);

            if (firstNameDetail != null || newDetail != null)
                changeDetail(firstNameDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(MiddleNameDetail.class)) {
            String text = detailToTextField.get(MiddleNameDetail.class).getText().toString();

            MiddleNameDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new MiddleNameDetail(text);

            if (middleNameDetail != null || newDetail != null)
                changeDetail(middleNameDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(LastNameDetail.class)) {
            String text = detailToTextField.get(LastNameDetail.class).getText().toString();
            LastNameDetail newDetail = null;

            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new LastNameDetail(text);

            if (lastNameDetail != null || newDetail != null)
                changeDetail(lastNameDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(NicknameDetail.class)) {
            String text = detailToTextField.get(NicknameDetail.class).getText().toString();

            NicknameDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new NicknameDetail(text);

            if (nicknameDetail != null || newDetail != null)
                changeDetail(nicknameDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(URLDetail.class)) {
            String text = detailToTextField.get(URLDetail.class).getText().toString().trim();

            URL url = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                try {
                    url = new URL(text);
                } catch (MalformedURLException e1) {
                    logger.debug("Failed to update URL detail due to malformed URL.");
                }

            URLDetail newDetail = null;
            if (url != null)
                newDetail = new URLDetail("URL", url);

            if (urlDetail != null || newDetail != null)
                changeDetail(urlDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(GenderDetail.class)) {
            String text = detailToTextField.get(GenderDetail.class).getText().toString();
            GenderDetail newDetail = null;

            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new GenderDetail(text);

            if (genderDetail != null || newDetail != null)
                changeDetail(genderDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(BirthDateDetail.class)) {
            String text = birthDateField.getText().toString();
            BirthDateDetail newDetail = null;

            if (!StringUtils.isNullOrEmpty(text, true)) {
                Calendar birthDate = Calendar.getInstance();
                try {
                    Date mDate = dateFormat.parse(text);
                    birthDate.setTime(mDate);
                    newDetail = new BirthDateDetail(birthDate);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
            if (birthDateDetail != null || newDetail != null)
                changeDetail(birthDateDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(AddressDetail.class)) {
            String text = detailToTextField.get(AddressDetail.class).getText().toString();

            AddressDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new AddressDetail(text);

            if (streetAddressDetail != null || newDetail != null)
                changeDetail(streetAddressDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(CityDetail.class)) {
            String text = detailToTextField.get(CityDetail.class).getText().toString();

            CityDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new CityDetail(text);

            if (cityDetail != null || newDetail != null)
                changeDetail(cityDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(ProvinceDetail.class)) {
            String text = detailToTextField.get(ProvinceDetail.class).getText().toString();

            ProvinceDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new ProvinceDetail(text);

            if (regionDetail != null || newDetail != null)
                changeDetail(regionDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(PostalCodeDetail.class)) {
            String text = detailToTextField.get(PostalCodeDetail.class).getText().toString();

            PostalCodeDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new PostalCodeDetail(text);

            if (postalCodeDetail != null || newDetail != null)
                changeDetail(postalCodeDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(CountryDetail.class)) {
            String text = detailToTextField.get(CountryDetail.class).getText().toString();

            CountryDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new CountryDetail(text);

            if (countryDetail != null || newDetail != null)
                changeDetail(countryDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(EmailAddressDetail.class)) {
            String text = detailToTextField.get(EmailAddressDetail.class).getText().toString();

            EmailAddressDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new EmailAddressDetail(text);

            if (emailDetail != null || newDetail != null)
                changeDetail(emailDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(WorkEmailAddressDetail.class)) {
            String text = detailToTextField.get(WorkEmailAddressDetail.class).getText().toString();

            WorkEmailAddressDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new WorkEmailAddressDetail(text);

            if (workEmailDetail != null || newDetail != null)
                changeDetail(workEmailDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(PhoneNumberDetail.class)) {
            String text = detailToTextField.get(PhoneNumberDetail.class).getText().toString();

            PhoneNumberDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new PhoneNumberDetail(text);

            if (phoneDetail != null || newDetail != null)
                changeDetail(phoneDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(WorkPhoneDetail.class)) {
            String text = detailToTextField.get(WorkPhoneDetail.class).getText().toString();

            WorkPhoneDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new WorkPhoneDetail(text);

            if (workPhoneDetail != null || newDetail != null)
                changeDetail(workPhoneDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(
                MobilePhoneDetail.class)) {
            String text = detailToTextField.get(MobilePhoneDetail.class).getText().toString();

            MobilePhoneDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new MobilePhoneDetail(text);

            if (mobilePhoneDetail != null || newDetail != null)
                changeDetail(mobilePhoneDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(WorkOrganizationNameDetail.class)) {
            String text = detailToTextField.get(WorkOrganizationNameDetail.class)
                    .getText().toString();

            WorkOrganizationNameDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new WorkOrganizationNameDetail(text);

            if (organizationDetail != null || newDetail != null)
                changeDetail(organizationDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(JobTitleDetail.class)) {
            String text = detailToTextField.get(JobTitleDetail.class).getText().toString();

            JobTitleDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new JobTitleDetail(text);

            if (jobTitleDetail != null || newDetail != null)
                changeDetail(jobTitleDetail, newDetail);
        }

        if (accountInfoOpSet.isDetailClassSupported(AboutMeDetail.class)) {
            String text = aboutMeArea.getText().toString();

            AboutMeDetail newDetail = null;
            if (!StringUtils.isNullOrEmpty(text, true))
                newDetail = new AboutMeDetail(text);

            if (aboutMeDetail != null || newDetail != null)
                changeDetail(aboutMeDetail, newDetail);
        }

        try {
            //mainScrollPane.getVerticalScrollBar().setValue(0);
            accountInfoOpSet.save();
        } catch (OperationFailedException e1) {
            showAvatarChangeError();
        }
    }

    /**
     * A helper method to decide whether to add new
     * <tt>ServerStoredDetails</tt> or to replace an old one.
     *
     * @param oldDetail the detail to be replaced.
     * @param newDetail the replacement.
     */
    private void changeDetail(GenericDetail oldDetail, GenericDetail newDetail)
    {
        try {
            if (newDetail == null) {
                accountInfoOpSet.removeDetail(oldDetail);
            }
            else if (oldDetail == null) {
                accountInfoOpSet.addDetail(newDetail);
            }
            else {
                accountInfoOpSet.replaceDetail(oldDetail, newDetail);
            }
        } catch (ArrayIndexOutOfBoundsException | OperationFailedException e1) {
            logger.debug("Failed to update account details. " + mAccount.getAccountName() + " " + e1);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.presence_status_menu, menu);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int id = item.getItemId();
        if (id == R.id.remove) {
            RemoveAccountDialog.create(this, mAccount,
                    new RemoveAccountDialog.OnAccountRemovedListener()
                    {
                        @Override
                        public void onAccountRemoved(Account accID)
                        {
                            // Prevent from submitting status
                            hasStatusChanges = false;
                            hasChanges = false;
                            finish();
                        }
                    }).show();
            return true;
        }
        else if (id == R.id.account_settings) {
            Intent preferences = AccountPreferencesActivity.getIntent(this, mAccount.getAccountID());
            startActivity(preferences);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v.getId() == R.id.accountAvatar) {
            getMenuInflater().inflate(R.menu.avatar_menu, menu);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.avatar_ChoosePicture:
                onAvatarClicked(avatarView);
                return true;
            case R.id.avatar_RemovePicture:
                imageUrlField.setText(AVATAR_ICON_REMOVE);
                avatarView.setImageResource(R.drawable.personphoto);
                hasChanges = true;
                return true;
            case R.id.avatar_Cancel:
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * Method mapped to the avatar image clicked event. It starts the select image {@link Intent}
     *
     * @param avatarView the {@link View} that has been clicked
     */
    @SuppressWarnings("unused")
    public void onAvatarClicked(View avatarView)
    {
        if (mAccount.getAvatarOpSet() == null) {
            logger.warn("Avatar operation set is not supported by " + mAccount.getAccountName());
            return;
        }
        // Intent pickIntent = new Intent(Intent.ACTION_PICK_ACTIVITY);
        Intent gallIntent = new Intent(Intent.ACTION_GET_CONTENT);
        gallIntent.setType("image/*");
        // Intent camIntent = new Intent("android.media.action.IMAGE_CAPTURE");
        gallIntent.putExtra(Intent.EXTRA_TITLE, R.string.service_gui_SELECT_AVATAR);
        startActivityForResult(gallIntent, SELECT_IMAGE);
    }

    /**
     * Method handles callbacks from external {@link Intent} that retrieve avatar image
     *
     * @param requestCode the request code {@link #SELECT_IMAGE}
     * @param resultCode the result code
     * @param data the source {@link Intent} that returns the result
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (resultCode != RESULT_OK)
            return;

        switch (requestCode) {
            case SELECT_IMAGE:
                final OperationSetAvatar avatarOpSet = mAccount.getAvatarOpSet();
                if (avatarOpSet == null) {
                    logger.warn("No avatar operation set found for " + mAccount.getAccountName());
                    showAvatarChangeError();
                    break;
                }

                Uri uri = data.getData();
                if (uri == null) {
                    logger.error("No image data selected: " + data);
                    showAvatarChangeError();
                }
                else {
                    String fileName = "cropImage";
                    File tmpFile = new File(this.getCacheDir(), fileName);
                    Uri destinationUri = Uri.fromFile(tmpFile);
                    UCrop.of(uri, destinationUri)
                            .withAspectRatio(1, 1)
                            .withMaxResultSize(CROP_MAX_SIZE, CROP_MAX_SIZE)
                            .start(this);
                }
                break;

            case UCrop.REQUEST_CROP:
                final Uri resultUri = UCrop.getOutput(data);
                if (resultUri == null)
                    break;
                try {
                    Bitmap bmp = AndroidImageUtil.scaledBitmapFromContentUri(this, resultUri,
                            AVATAR_PREFERRED_SIZE, AVATAR_PREFERRED_SIZE);
                    if (bmp == null) {
                        logger.error("Failed to obtain bitmap from: " + data);
                        showAvatarChangeError();
                    }
                    else {
                        avatarView.setImageBitmap(bmp);
                        imageUrlField.setText(resultUri.toString());
                        hasChanges = true;
                    }
                } catch (IOException e) {
                    logger.error(e, e);
                    showAvatarChangeError();
                }
                break;

            case UCrop.RESULT_ERROR:
                final Throwable cropError = UCrop.getError(data);
                logger.error("Image crop error:" + cropError.getMessage());
                showAvatarChangeError();
                break;
        }
    }

    private void showAvatarChangeError()
    {
        AndroidUtils.showAlertDialog(this, getString(R.string.service_gui_ERROR), getString(R.string.service_gui_AVATAR_SET_ERROR, mAccount.getAccountName()));
    }

    /**
     * Method starts a new Thread and publishes the status
     *
     * @param status {@link PresenceStatus} to be set
     * @param text the status message
     */
    private void publishStatus(final PresenceStatus status, final String text)
    {
        new Thread(new Runnable()
        {
            public void run()
            {
                try {
                    // Try to publish selected status
                    logger.trace("Publishing status " + status + " msg: " + text);
                    GlobalStatusService globalStatus
                            = ServiceUtils.getService(AndroidGUIActivator.bundleContext, GlobalStatusService.class);

                    ProtocolProviderService pps = mAccount.getProtocolProvider();
                    // cmeng: set state to false to force it to execute offline->online
                    if (globalStatus != null)
                        globalStatus.publishStatus(pps, status, false);
                    if (pps.isRegistered())
                        accountPresence.publishPresenceStatus(status, text);
                } catch (Exception e) {
                    logger.error(e);

                    // TODO: fix me
                    // This is annoying and removed for now. It is displayed for XMPP provider
                    // even when switching from offline to any other status.
                    /*
                     * AndroidUtils.showAlertDialog( getBaseContext(), "Error",
                     * "An error occurred while setting the status: " + e.getLocalizedMessage());
                     */
                }
            }
        }).start();
    }

    /**
     * Fired when the {@link #mAccount} has changed and the UI need to be updated
     *
     * @param eventObject the instance that has been changed
     * cmeng: may not be required anymore with new implementation
     */
    public void onChangeEvent(final AccountEvent eventObject)
    {
        if (eventObject.getEventType() != AccountEvent.AVATAR_CHANGE) {
            return;
        }

        runOnUiThread(new Runnable()
        {
            public void run()
            {
                Account account = eventObject.getSource();
                avatarView.setImageDrawable(account.getAvatarIcon());
            }
        });
    }

    /**
     * Checks if there are any uncommitted changes and applies them eventually
     */
    private void commitStatusChanges()
    {
        if (hasStatusChanges) {
            Spinner statusSpinner = findViewById(R.id.presenceStatusSpinner);
            EditText statusMessageEdit = findViewById(R.id.statusMessage);

            PresenceStatus selectedStatus = (PresenceStatus) statusSpinner.getSelectedItem();
            String statusMessageText = statusMessageEdit.getText().toString();

            if ((selectedStatus.getStatus() == PresenceStatus.OFFLINE) && (hasChanges)) {
                // abort all account info changes if user goes offline
                hasChanges = false;

                if (progressDialog != null) {
                    progressDialog.setMessage(getString(R.string.plugin_accountinfo_DISCARD_CHANGE));
                }
            }
            // Publish status in new thread
            publishStatus(selectedStatus, statusMessageText);
        }
    }

    /**
     * Fired when new presence status is selected
     *
     * @param adapterView clicked adapter's View
     * @param view selected item's View
     * @param i items position
     * @param l item's id
     */
    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l)
    {
        // Need to ignore the first trigger when adapter is being first init
        if (!firstInit)
            hasStatusChanges = true;
        else
            firstInit = false;
    }

    public void onNothingSelected(AdapterView<?> adapterView)
    {
        // Should not happen in single selection mode
    }

    /**
     * Class responsible for creating the Views for a given set of {@link PresenceStatus}
     */
    private class StatusListAdapter extends CollectionAdapter<PresenceStatus>
    {
        /**
         * Creates new instance of {@link StatusListAdapter}
         *
         * @param objects {@link Iterator} for a set of {@link PresenceStatus}
         */
        private StatusListAdapter(Iterator<PresenceStatus> objects)
        {
            super(AccountInfoPresenceActivity.this, objects);
        }

        @Override
        protected View getView(boolean isDropDown, PresenceStatus item, ViewGroup parent, LayoutInflater inflater)
        {
            // Retrieve views
            View statusItemView = inflater.inflate(R.layout.account_presence_status_row, parent, false);
            TextView statusNameView = (TextView) statusItemView.findViewById(R.id.presenceStatusNameView);
            ImageView statusIconView = (ImageView) statusItemView.findViewById(R.id.presenceStatusIconView);

            // Set status name
            String statusName = item.getStatusName();
            statusNameView.setText(statusName);

            // Set status icon
            Bitmap presenceIcon = AndroidImageUtil.bitmapFromBytes(item.getStatusIcon());
            statusIconView.setImageBitmap(presenceIcon);
            return statusItemView;
        }

        /**
         * Find the position of <tt>status</tt> in adapter's list
         *
         * @param status the {@link PresenceStatus} for which the position is returned
         * @return index of <tt>status</tt> in adapter's list
         */
        int getPositionForItem(PresenceStatus status)
        {
            for (int i = 0; i < getCount(); i++) {
                PresenceStatus other = (PresenceStatus) getItem(i);
                if (other.equals(status))
                    return i;
            }
            return -1;
        }
    }

    /**
     * Progressing dialog while applying changes to account info/status
     * Auto cancel the dialog at end of applying cycle
     */
    public void launchApplyProgressDialog()
    {
        progressDialog = ProgressDialog.show(this, getString(R.string.service_gui_WAITING),
                getString(R.string.service_gui_APPLY_CHANGES), true);
        progressDialog.setCancelable(true);
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    commitStatusChanges();
                    SubmitChangesAction();
                    // too fast to be viewed user at times - so pause for 2.0 seconds
                    Thread.sleep(2000);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                progressDialog.dismiss();
                finish();
            }
        }).start();
    }

    /**
     * SoftKeyboard event handler to show/hide view buttons to give more space
     * for fields' text entry.
     * # init to handle when softKeyboard is hided/shown
     */
    private void initSoftKeyboard()
    {
        LinearLayout mainLayout = findViewById(R.id.accountInfo_layout);
        InputMethodManager im = (InputMethodManager) getSystemService(Service.INPUT_METHOD_SERVICE);

        /*  Instantiate and pass a callback */
        softKeyboard = new SoftKeyboard(mainLayout, im);
        softKeyboard.setSoftKeyboardCallback(this);
    }

    // Events to show or hide buttons for bigger view space for text entry
    @Override
    public void onSoftKeyboardHide()
    {
        new Handler(Looper.getMainLooper()).post(new Runnable()
        {
            @Override
            public void run()
            {
                mButtonContainer.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onSoftKeyboardShow()
    {
        new Handler(Looper.getMainLooper()).post(new Runnable()
        {
            @Override
            public void run()
            {
                mButtonContainer.setVisibility(View.GONE);
            }
        });
    }

    private class EditTextWatcher implements TextWatcher
    {

        public void beforeTextChanged(CharSequence s, int start, int count, int after)
        {
            // Ignore
        }

        public void onTextChanged(CharSequence s, int start, int before, int count)
        {
        }

        public void afterTextChanged(Editable s)
        {
            hasChanges = true;
        }
    }
}
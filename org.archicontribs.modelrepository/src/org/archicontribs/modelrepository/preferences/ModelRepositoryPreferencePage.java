/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.modelrepository.preferences;

import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;

import org.archicontribs.modelrepository.ModelRepositoryPlugin;
import org.archicontribs.modelrepository.authentication.EncryptedCredentialsStorage;
import org.archicontribs.modelrepository.dialogs.NewPrimaryPasswordDialog;
import org.archicontribs.modelrepository.grafico.GraficoUtils;
import org.archicontribs.modelrepository.grafico.IGraficoConstants;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.events.VerifyListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.ui.UIUtils;


/**
 * Model Repository Preferences Page
 * 
 * @author Phillip Beauvoir
 */
public class ModelRepositoryPreferencePage
extends PreferencePage
implements IWorkbenchPreferencePage, IPreferenceConstants {
    
    public static final String ID = "org.archicontribs.modelrepository.preferences.ModelRepositoryPreferencePage";  //$NON-NLS-1$
    
    private static final String HELP_ID = "org.archicontribs.modelrepository.prefsValidator"; //$NON-NLS-1$
    
    private Text fUserNameTextField;
    private Text fUserEmailTextField;
    
    private Text fUserRepoFolderTextField;
    
    private Button fSSHIdentitySelectButton;
    private Text fSSHIdentityFileTextField;
    private Button fSSHIdentityRequiresPasswordButton;
    private Text fSSHIdentityPasswordTextField;
    
    private Button fStoreCredentialsButton;
    
    private Button fFetchInBackgroundButton;
    private Spinner fFetchInBackgroundIntervalSpinner;
    
    private Button fUseProxyButton, fRequiresProxyAuthenticationButton;
    private Text fProxyHostTextField;
    private Text fProxyPortTextField;
    private Text fProxyUserNameTextField;
    private Text fProxyUserPasswordTextField;
    
    private boolean sshPasswordChanged;
    private boolean proxyUsernameChanged;
    private boolean proxyPasswordChanged;
    
    private Button fChangePrimaryPasswordButton;
    
	public ModelRepositoryPreferencePage() {
		setPreferenceStore(ModelRepositoryPlugin.INSTANCE.getPreferenceStore());
	}
	
    @Override
    protected Control createContents(Composite parent) {
        // Help
        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, HELP_ID);

        Composite client = new Composite(parent, SWT.NULL);
        GridLayout layout = new GridLayout();
        layout.marginWidth = layout.marginHeight = 0;
        client.setLayout(layout);
        
        // User details
        Group userDetailsGroup = new Group(client, SWT.NULL);
        userDetailsGroup.setText(Messages.ModelRepositoryPreferencePage_1);
        userDetailsGroup.setLayout(new GridLayout(2, false));
        userDetailsGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        Label label = new Label(userDetailsGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_2);
        
        fUserNameTextField = UIUtils.createSingleTextControl(userDetailsGroup, SWT.BORDER, false);
        fUserNameTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        // Single text control so strip CRLFs

        label = new Label(userDetailsGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_3);
        
        fUserEmailTextField = UIUtils.createSingleTextControl(userDetailsGroup, SWT.BORDER, false);
        fUserEmailTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        // Workspace Group
        Group workspaceGroup = new Group(client, SWT.NULL);
        workspaceGroup.setText(Messages.ModelRepositoryPreferencePage_4);
        workspaceGroup.setLayout(new GridLayout(3, false));
        GridData gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.widthHint = 500;
        workspaceGroup.setLayoutData(gd);
        
        // Workspace folder location
        label = new Label(workspaceGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_5);
        
        fUserRepoFolderTextField = UIUtils.createSingleTextControl(workspaceGroup, SWT.BORDER, false);
        fUserRepoFolderTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        Button folderButton = new Button(workspaceGroup, SWT.PUSH);
        folderButton.setText(Messages.ModelRepositoryPreferencePage_6);
        folderButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String folderPath = chooseFolderPath();
                if(folderPath != null) {
                    fUserRepoFolderTextField.setText(folderPath);
                }
            }
        });
        
        // Fetch in background
        fFetchInBackgroundButton = new Button(workspaceGroup, SWT.CHECK);
        fFetchInBackgroundButton.setText(Messages.ModelRepositoryPreferencePage_19);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 3;
        fFetchInBackgroundButton.setLayoutData(gd);
        
        // Refresh interval
        label = new Label(workspaceGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_21);

        fFetchInBackgroundIntervalSpinner = new Spinner(workspaceGroup, SWT.BORDER);
        fFetchInBackgroundIntervalSpinner.setMinimum(30);
        fFetchInBackgroundIntervalSpinner.setMaximum(3000);

        
        // Authentication
        Group authGroup = new Group(client, SWT.NULL);
        authGroup.setText(Messages.ModelRepositoryPreferencePage_0);
        authGroup.setLayout(new GridLayout());
        authGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        // Primary password
        fChangePrimaryPasswordButton = new Button(authGroup, SWT.PUSH);
        fChangePrimaryPasswordButton.setText(Messages.ModelRepositoryPreferencePage_25);
        fChangePrimaryPasswordButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updatePrimaryPassword();
            }
        });
        
        // SSH Credentials
        Group sshGroup = new Group(authGroup, SWT.NULL);
        sshGroup.setText(Messages.ModelRepositoryPreferencePage_7);
        sshGroup.setLayout(new GridLayout(3, false));
        sshGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        label = new Label(sshGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_24);
        
        fSSHIdentityFileTextField = UIUtils.createSingleTextControl(sshGroup, SWT.BORDER, false);
        fSSHIdentityFileTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        fSSHIdentitySelectButton = new Button(sshGroup, SWT.PUSH);
        fSSHIdentitySelectButton.setText(Messages.ModelRepositoryPreferencePage_6);
        fSSHIdentitySelectButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                String identityFile = chooseIdentityFile();
                if(identityFile != null) {
                    fSSHIdentityFileTextField.setText(identityFile);
                }
            }
        });
        
        fSSHIdentityRequiresPasswordButton = new Button(sshGroup, SWT.CHECK);
        fSSHIdentityRequiresPasswordButton.setText(Messages.ModelRepositoryPreferencePage_22);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 4;
        fSSHIdentityRequiresPasswordButton.setLayoutData(gd);
        fSSHIdentityRequiresPasswordButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateIdentityControls();
            }
        });
        
        label = new Label(sshGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_23);
        fSSHIdentityPasswordTextField = UIUtils.createSingleTextControl(sshGroup, SWT.BORDER | SWT.PASSWORD, false);
        fSSHIdentityPasswordTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        fSSHIdentityPasswordTextField.setEnabled(false);
        
        
        
        // HTTP Credentials
        Group httpGroup = new Group(authGroup, SWT.NULL);
        httpGroup.setText(Messages.ModelRepositoryPreferencePage_9);
        httpGroup.setLayout(new GridLayout(1, false));
        httpGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        fStoreCredentialsButton = new Button(httpGroup, SWT.CHECK);
        fStoreCredentialsButton.setText(Messages.ModelRepositoryPreferencePage_8);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        fStoreCredentialsButton.setLayoutData(gd);
        
        //HTTP Skip SSL verification 
        
        
        
        // Proxy Group
        Group proxyGroup = new Group(client, SWT.NULL);
        proxyGroup.setText(Messages.ModelRepositoryPreferencePage_10);
        proxyGroup.setLayout(new GridLayout(4, false));
        proxyGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        
        fUseProxyButton = new Button(proxyGroup, SWT.CHECK);
        fUseProxyButton.setText(Messages.ModelRepositoryPreferencePage_11);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 4;
        fUseProxyButton.setLayoutData(gd);
        fUseProxyButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateProxyControls();
            }
        });
        
        label = new Label(proxyGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_12);
        fProxyHostTextField = UIUtils.createSingleTextControl(proxyGroup, SWT.BORDER, false);
        fProxyHostTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        fProxyHostTextField.setEnabled(false);

        label = new Label(proxyGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_13);
        fProxyPortTextField = UIUtils.createSingleTextControl(proxyGroup, SWT.BORDER, false);
        fProxyPortTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        fProxyPortTextField.setEnabled(false);
        
        fProxyPortTextField.addVerifyListener(new VerifyListener() {
            @Override
            public void verifyText(VerifyEvent e) {
                String currentText = ((Text)e.widget).getText();
                String port = currentText.substring(0, e.start) + e.text + currentText.substring(e.end);
                try {
                    int portNum = Integer.valueOf(port);
                    if(portNum < 0 || portNum > 65535) {
                        e.doit = false;
                    }
                }
                catch(NumberFormatException ex) {
                    if(!port.equals("")) { //$NON-NLS-1$
                        e.doit = false;
                    }
                }
            }
        });
        
        fRequiresProxyAuthenticationButton = new Button(proxyGroup, SWT.CHECK);
        fRequiresProxyAuthenticationButton.setText(Messages.ModelRepositoryPreferencePage_14);
        gd = new GridData(GridData.FILL_HORIZONTAL);
        gd.horizontalSpan = 4;
        fRequiresProxyAuthenticationButton.setLayoutData(gd);
        fRequiresProxyAuthenticationButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                updateProxyControls();
            }
        });
        
        label = new Label(proxyGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_15);
        fProxyUserNameTextField = UIUtils.createSingleTextControl(proxyGroup, SWT.BORDER, false);
        fProxyUserNameTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        fProxyUserNameTextField.setEnabled(false);
        
        label = new Label(proxyGroup, SWT.NULL);
        label.setText(Messages.ModelRepositoryPreferencePage_16);
        fProxyUserPasswordTextField = UIUtils.createSingleTextControl(proxyGroup, SWT.BORDER | SWT.PASSWORD, false);
        fProxyUserPasswordTextField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        fProxyUserPasswordTextField.setEnabled(false);

        setValues();
        
        return client;
    }

    private String chooseIdentityFile() {
        FileDialog dialog = new FileDialog(Display.getCurrent().getActiveShell());
        dialog.setText(Messages.ModelRepositoryPreferencePage_20);
//        dialog.setMessage(Messages.ModelRepositoryPreferencePage_21);
        File file = new File(fSSHIdentityFileTextField.getText());
        if(file.exists()) {
            dialog.setFilterPath(fSSHIdentityFileTextField.getText());
        }
        return dialog.open();
    }

    private String chooseFolderPath() {
        DirectoryDialog dialog = new DirectoryDialog(Display.getCurrent().getActiveShell());
        dialog.setText(Messages.ModelRepositoryPreferencePage_17);
        dialog.setMessage(Messages.ModelRepositoryPreferencePage_18);
        File file = new File(fUserRepoFolderTextField.getText());
        if(file.exists()) {
            dialog.setFilterPath(fUserRepoFolderTextField.getText());
        }
        return dialog.open();
    }

    private void setValues() {
        // Gobal user details
        PersonIdent result = getUserDetails();
        fUserNameTextField.setText(result.getName());
        fUserEmailTextField.setText(result.getEmailAddress());
        
        // Workspace folder
        fUserRepoFolderTextField.setText(getPreferenceStore().getString(PREFS_REPOSITORY_FOLDER));
        
        // Refresh in background
        fFetchInBackgroundButton.setSelection(getPreferenceStore().getBoolean(PREFS_FETCH_IN_BACKGROUND));
        fFetchInBackgroundIntervalSpinner.setSelection(getPreferenceStore().getInt(PREFS_FETCH_IN_BACKGROUND_INTERVAL));

        // SSH details
        fSSHIdentityFileTextField.setText(getPreferenceStore().getString(PREFS_SSH_IDENTITY_FILE));
        fSSHIdentityRequiresPasswordButton.setSelection(getPreferenceStore().getBoolean(PREFS_SSH_IDENTITY_REQUIRES_PASSWORD));
        
        try {
            EncryptedCredentialsStorage sshCredentials = getSSHCredentials();
            fSSHIdentityPasswordTextField.setText(sshCredentials.hasPassword() ? "********" : ""); //$NON-NLS-1$ //$NON-NLS-2$
            
            fSSHIdentityPasswordTextField.addModifyListener(event -> {
                sshPasswordChanged = true;
            });
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        
        // Store HTTP details by default
        fStoreCredentialsButton.setSelection(getPreferenceStore().getBoolean(PREFS_STORE_REPO_CREDENTIALS));
        
        // Proxy details
        fUseProxyButton.setSelection(getPreferenceStore().getBoolean(PREFS_PROXY_USE));
        fProxyHostTextField.setText(getPreferenceStore().getString(PREFS_PROXY_HOST));
        fProxyPortTextField.setText(getPreferenceStore().getString(PREFS_PROXY_PORT));
        fRequiresProxyAuthenticationButton.setSelection(getPreferenceStore().getBoolean(PREFS_PROXY_REQUIRES_AUTHENTICATION));
        
        try {
            EncryptedCredentialsStorage proxyCredentials = getProxyCredentials();
            fProxyUserNameTextField.setText(proxyCredentials.getUserName());
            fProxyUserPasswordTextField.setText(proxyCredentials.hasPassword() ? "********" : ""); //$NON-NLS-1$ //$NON-NLS-2$
            
            fProxyUserNameTextField.addModifyListener(event -> {
                proxyUsernameChanged = true;
            });
            
            fProxyUserPasswordTextField.addModifyListener(event -> {
                proxyPasswordChanged = true;
            });
        }
        catch(IOException ex) {
            ex.printStackTrace();
        }
        
        updateIdentityControls();
        updateProxyControls();
    }
    
    @Override
    public boolean performOk() {
        String name = fUserNameTextField.getText();
        String email = fUserEmailTextField.getText();
        
        try {
            GraficoUtils.saveGitConfigUserDetails(name, email);
        }
        catch(IOException | ConfigInvalidException ex) {
            ex.printStackTrace();
        }
        
        getPreferenceStore().setValue(PREFS_REPOSITORY_FOLDER, fUserRepoFolderTextField.getText());
        getPreferenceStore().setValue(PREFS_SSH_IDENTITY_FILE, fSSHIdentityFileTextField.getText());
        getPreferenceStore().setValue(PREFS_SSH_IDENTITY_REQUIRES_PASSWORD, fSSHIdentityRequiresPasswordButton.getSelection());
        
        getPreferenceStore().setValue(PREFS_FETCH_IN_BACKGROUND, fFetchInBackgroundButton.getSelection());
        getPreferenceStore().setValue(PREFS_FETCH_IN_BACKGROUND_INTERVAL, fFetchInBackgroundIntervalSpinner.getSelection());
        
        getPreferenceStore().setValue(PREFS_STORE_REPO_CREDENTIALS, fStoreCredentialsButton.getSelection());
        
        getPreferenceStore().setValue(PREFS_PROXY_USE, fUseProxyButton.getSelection());
        getPreferenceStore().setValue(PREFS_PROXY_HOST, fProxyHostTextField.getText());
        getPreferenceStore().setValue(PREFS_PROXY_PORT, fProxyPortTextField.getText());
        getPreferenceStore().setValue(PREFS_PROXY_REQUIRES_AUTHENTICATION, fRequiresProxyAuthenticationButton.getSelection());
                
        // If "requires password" selected
        if(fSSHIdentityRequiresPasswordButton.getSelection()) {
            // If password changed
            if(sshPasswordChanged) {
                try {
                    EncryptedCredentialsStorage sshCredentials = getSSHCredentials();
                    sshCredentials.storePassword(fSSHIdentityPasswordTextField.getTextChars());
                }
                catch(GeneralSecurityException | IOException ex) {
                    ex.printStackTrace();
                    return false;
                }
            }
        }
        
        // If "use proxy" selected
        if(fUseProxyButton.getSelection()) {
            EncryptedCredentialsStorage proxyCredentials = getProxyCredentials();

            try {
                // Username changed
                if(proxyUsernameChanged) {
                    proxyCredentials.storeUserName(fProxyUserNameTextField.getText());
                }
                // Password changed
                if(proxyPasswordChanged) {
                    proxyCredentials.storePassword(fProxyUserPasswordTextField.getTextChars());
                }
            }
            catch(IOException | GeneralSecurityException ex) {
                ex.printStackTrace();
                return false;
            }
        }
       
        return true;
    }
    
    @Override
    protected void performDefaults() {
        PersonIdent result = getUserDetails();
        fUserNameTextField.setText(result.getName());
        fUserEmailTextField.setText(result.getEmailAddress());

        fSSHIdentityFileTextField.setText(getPreferenceStore().getDefaultString(PREFS_SSH_IDENTITY_FILE));
        fSSHIdentityRequiresPasswordButton.setSelection(getPreferenceStore().getDefaultBoolean(PREFS_SSH_IDENTITY_REQUIRES_PASSWORD));
        fSSHIdentityPasswordTextField.setText(""); //$NON-NLS-1$
        
        fUserRepoFolderTextField.setText(getPreferenceStore().getDefaultString(PREFS_REPOSITORY_FOLDER));
        
        fFetchInBackgroundButton.setSelection(getPreferenceStore().getDefaultBoolean(PREFS_FETCH_IN_BACKGROUND));
        fFetchInBackgroundIntervalSpinner.setSelection(getPreferenceStore().getDefaultInt(PREFS_FETCH_IN_BACKGROUND_INTERVAL));
        
        fStoreCredentialsButton.setSelection(getPreferenceStore().getDefaultBoolean(PREFS_STORE_REPO_CREDENTIALS));
        
        fUseProxyButton.setSelection(getPreferenceStore().getDefaultBoolean(PREFS_PROXY_USE));
        fProxyHostTextField.setText(getPreferenceStore().getDefaultString(PREFS_PROXY_HOST));
        fProxyPortTextField.setText(getPreferenceStore().getDefaultString(PREFS_PROXY_PORT));
        fRequiresProxyAuthenticationButton.setSelection(getPreferenceStore().getDefaultBoolean(PREFS_PROXY_REQUIRES_AUTHENTICATION));
        fProxyUserNameTextField.setText(""); //$NON-NLS-1$
        fProxyUserPasswordTextField.setText(""); //$NON-NLS-1$
        
        updateIdentityControls();
        updateProxyControls();
    }
    
    
    private void updatePrimaryPassword() {
        NewPrimaryPasswordDialog dialog = new NewPrimaryPasswordDialog(getShell());
        dialog.open();
    }
    
    private void updateIdentityControls() {
    	fSSHIdentityPasswordTextField.setEnabled(fSSHIdentityRequiresPasswordButton.getSelection());
    }
    
    private void updateProxyControls() {
        fProxyHostTextField.setEnabled(fUseProxyButton.getSelection());
        fProxyPortTextField.setEnabled(fUseProxyButton.getSelection());

        fRequiresProxyAuthenticationButton.setEnabled(fUseProxyButton.getSelection());
        fProxyUserNameTextField.setEnabled(fUseProxyButton.getSelection() && fRequiresProxyAuthenticationButton.getSelection());
        fProxyUserPasswordTextField.setEnabled(fUseProxyButton.getSelection() && fRequiresProxyAuthenticationButton.getSelection());
    }
    
    private PersonIdent getUserDetails() {
        try {
            return GraficoUtils.getGitConfigUserDetails();
        }
        catch(IOException | ConfigInvalidException ex) {
            ex.printStackTrace();
        }
        
        // Default
        return new PersonIdent(getPreferenceStore().getDefaultString(PREFS_COMMIT_USER_NAME), getPreferenceStore().getDefaultString(PREFS_COMMIT_USER_EMAIL));
    }
    
    private EncryptedCredentialsStorage getSSHCredentials() {
        return new EncryptedCredentialsStorage(new File(ModelRepositoryPlugin.INSTANCE.getUserModelRepositoryFolder(),
                IGraficoConstants.SSH_CREDENTIALS_FILE));
    }
    
    private EncryptedCredentialsStorage getProxyCredentials() {
        return new EncryptedCredentialsStorage(new File(ModelRepositoryPlugin.INSTANCE.getUserModelRepositoryFolder(),
                IGraficoConstants.PROXY_CREDENTIALS_FILE));
    }
    
    @Override
    public void init(IWorkbench workbench) {
    }
}
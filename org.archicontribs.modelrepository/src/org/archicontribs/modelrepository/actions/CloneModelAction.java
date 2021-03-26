/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.modelrepository.actions;

import java.io.File;
import java.net.URI;
import java.security.GeneralSecurityException;

import org.archicontribs.modelrepository.IModelRepositoryImages;
import org.archicontribs.modelrepository.ModelRepositoryPlugin;
import org.archicontribs.modelrepository.authentication.EncryptedCredentialsStorage;
import org.archicontribs.modelrepository.authentication.ProxyAuthenticator;
import org.archicontribs.modelrepository.authentication.UsernamePassword;
import org.archicontribs.modelrepository.dialogs.CloneInputDialog;
import org.archicontribs.modelrepository.grafico.ArchiRepository;
import org.archicontribs.modelrepository.grafico.GraficoModelLoader;
import org.archicontribs.modelrepository.grafico.GraficoUtils;
import org.archicontribs.modelrepository.grafico.IRepositoryListener;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.window.Window;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.SystemReader;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.progress.IProgressService;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;

/**
 * Clone a model
 * 
 * 1. Check Primary Key
 * 2. Get user credentials
 * 3. Clone from Remote
 * 4. If Grafico files exist load the model from the Grafico files and save it as temp file 
 * 5. If Grafico files do not exist create a new temp model and save it 
 * 6. Store user credentials if prefs agree
 */
public class CloneModelAction extends AbstractModelAction {

	public CloneModelAction(IWorkbenchWindow window) {
		super(window);
		setImageDescriptor(IModelRepositoryImages.ImageFactory.getImageDescriptor(IModelRepositoryImages.ICON_CLONE));
		setText(Messages.CloneModelAction_0);
		setToolTipText(Messages.CloneModelAction_0);
	}

	@Override
	public void run() {
		// Check primary key set
		try {
			if (!EncryptedCredentialsStorage.checkPrimaryKeySet()) {
				return;
			}
		} catch (GeneralSecurityException ex) {
			displayCredentialsErrorDialog(ex);
			return;
		} catch (Exception ex) {
			displayErrorDialog(Messages.CloneModelAction_0, ex);
			return;
		}

		CloneInputDialog dialog = new CloneInputDialog(fWindow.getShell());
		if (dialog.open() != Window.OK) {
			return;
		}

		final String repoURL = dialog.getURL();
		final boolean storeCredentials = dialog.doStoreCredentials();
		final UsernamePassword npw = dialog.getUsernamePassword();
		final boolean skipSSLVerification = dialog.skipSSLverification();

		if (!StringUtils.isSet(repoURL)) {
			return;
		}

		if (GraficoUtils.isHTTP(repoURL) && !StringUtils.isSet(npw.getUsername()) && npw.getPassword().length == 0) {
			MessageDialog.openError(fWindow.getShell(), Messages.CloneModelAction_0, Messages.CloneModelAction_1);
			return;
		}

		// Create a new local folder
		File localRepoFolder = GraficoUtils
				.getUniqueLocalFolder(ModelRepositoryPlugin.INSTANCE.getUserModelRepositoryFolder(), repoURL);
		setRepository(new ArchiRepository(localRepoFolder));

		if (skipSSLVerification)
			disableSSLVerify(repoURL);
		try {
			// Clone
			Exception[] exception = new Exception[1];
			IProgressService ps = PlatformUI.getWorkbench().getProgressService();
			ps.busyCursorWhile(new IRunnableWithProgress() {
				@Override
				public void run(IProgressMonitor pm) {
					try {
						// Update Proxy
						ProxyAuthenticator.update();

						pm.beginTask(Messages.CloneModelAction_4, -1);
						getRepository().cloneModel(repoURL, npw, new ProgressMonitorWrapper(pm));
					} catch (Exception ex) {
						exception[0] = ex;
					} finally {
						// Clear Proxy
						ProxyAuthenticator.clear();
					}
				}
			});

			if (exception[0] != null) {
				throw exception[0];
			}

			// Load it from the Grafico files if we can
			IArchimateModel graficoModel = new GraficoModelLoader(getRepository()).loadModel();

			// We couldn't load it from Grafico so create a new blank model
			if (graficoModel == null) {
				// New one. This will open in the tree
				IArchimateModel model = IEditorModelManager.INSTANCE.createNewModel();
				model.setFile(getRepository().getTempModelFile());

				// And Save it
				IEditorModelManager.INSTANCE.saveModel(model);

				// Export to Grafico
				getRepository().exportModelToGraficoFiles();

				// And do a first commit
				getRepository().commitChanges(Messages.CloneModelAction_3, false);

				// Save the checksum
				getRepository().saveChecksum();
			}

			// Store repo credentials if HTTP and option is set
			if (GraficoUtils.isHTTP(repoURL) && storeCredentials) {
				EncryptedCredentialsStorage cs = EncryptedCredentialsStorage.forRepository(getRepository());
				cs.store(npw);
			}

			// Notify listeners
			notifyChangeListeners(IRepositoryListener.REPOSITORY_ADDED);
		} catch (Exception ex) {
			displayErrorDialog(Messages.CloneModelAction_0, ex);
		}
	}

	private void disableSSLVerify(String repoURL) {
		try {

			URI gitServer = new URI(repoURL);
			if (gitServer.getScheme().equals("https")) {
				FileBasedConfig config = SystemReader.getInstance().openUserConfig(null, FS.DETECTED);
				synchronized (config) {
					config.load();
					config.setBoolean("http", "https://" + gitServer.getHost() + ':'
							+ (gitServer.getPort() == -1 ? 443 : gitServer.getPort()), "sslVerify", false);
					config.save();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	protected boolean shouldBeEnabled() {
		return true;
	}
}

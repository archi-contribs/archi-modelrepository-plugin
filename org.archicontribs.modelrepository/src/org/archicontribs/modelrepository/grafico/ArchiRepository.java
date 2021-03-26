/**
 * This program and the accompanying materials
 * are made available under the terms of the License
 * which accompanies this distribution in the file LICENSE.txt
 */
package org.archicontribs.modelrepository.grafico;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;

import org.archicontribs.modelrepository.authentication.CredentialsAuthenticator;
import org.archicontribs.modelrepository.authentication.UsernamePassword;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CleanCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.PullCommand;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.RemoteAddCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.ResetCommand.ResetType;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ConfigConstants;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.ui.PlatformUI;

import com.archimatetool.editor.model.IEditorModelManager;
import com.archimatetool.editor.utils.StringUtils;
import com.archimatetool.model.IArchimateModel;

/**
 * Representation of a local repository This is a wrapper class around a local
 * repo folder
 * 
 * @author Phillip Beauvoir
 */
public class ArchiRepository implements IArchiRepository {

	/**
	 * The folder location of the local repository
	 */
	private File fLocalRepoFolder;

	public ArchiRepository(File localRepoFolder) {
		fLocalRepoFolder = localRepoFolder;
	}

	@Override
	public File getLocalRepositoryFolder() {
		return fLocalRepoFolder;
	}

	@Override
	public File getLocalGitFolder() {
		return new File(getLocalRepositoryFolder(), ".git"); //$NON-NLS-1$
	}

	@Override
	public String getName() {
		String[] result = new String[1];

		// Find the "folder.xml" file and read it from there
		File file = new File(getLocalRepositoryFolder(),
				IGraficoConstants.MODEL_FOLDER + "/" + IGraficoConstants.FOLDER_XML); //$NON-NLS-1$
		if (file.exists()) {
			try (Stream<String> stream = Files.lines(Paths.get(file.getAbsolutePath()))) {
				stream.forEach(s -> {
					if (result[0] == null && s.indexOf("name=") != -1) { //$NON-NLS-1$
						String segments[] = s.split("\""); //$NON-NLS-1$
						if (segments.length == 2) {
							result[0] = segments[1];
						}
					}
				});
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		return result[0] != null ? result[0] : fLocalRepoFolder.getName();
	}

	@Override
	public File getTempModelFile() {
		return new File(getLocalRepositoryFolder(), "/.git/" + IGraficoConstants.LOCAL_ARCHI_FILENAME); //$NON-NLS-1$
	}

	@Override
	public String getOnlineRepositoryURL() throws IOException {
		try (Git git = Git.open(getLocalRepositoryFolder())) {
			return git.getRepository().getConfig().getString("remote", IGraficoConstants.ORIGIN, "url"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	@Override
	public IArchimateModel locateModel() {
		File tempFile = getTempModelFile();

		for (IArchimateModel model : IEditorModelManager.INSTANCE.getModels()) {
			if (tempFile.equals(model.getFile())) {
				return model;
			}
		}

		return null;
	}

	@Override
	public boolean hasChangesToCommit() throws IOException, GitAPIException {
		try (Git git = Git.open(getLocalRepositoryFolder())) {
			Status status = git.status().call();
			return !status.isClean();
		}
	}

	@Override
	public RevCommit commitChanges(String commitMessage, boolean amend) throws GitAPIException, IOException {
		try (Git git = Git.open(getLocalRepositoryFolder())) {
			Status status = git.status().call();

			// Nothing changed
			if (status.isClean()) {
				return null;
			}

			// Check lock file is deleted
			checkDeleteLockFile();

			// Add modified files to index
			AddCommand addCommand = git.add();
			addCommand.addFilepattern("."); //$NON-NLS-1$
			addCommand.setUpdate(false);
			addCommand.call();

			// Add missing files to index
			for (String s : status.getMissing()) {
				git.rm().addFilepattern(s).call();
			}

			// Commit
			CommitCommand commitCommand = git.commit();
			PersonIdent userDetails = getUserDetails();
			commitCommand.setAuthor(userDetails);
			commitCommand.setMessage(commitMessage);
			commitCommand.setAmend(amend);
			return commitCommand.call();
		}
	}

	@Override
	public void cloneModel(String repoURL, UsernamePassword npw, ProgressMonitor monitor)
			throws GitAPIException, IOException {

		CloneCommand cloneCommand = Git.cloneRepository();
		cloneCommand.setDirectory(getLocalRepositoryFolder());
		cloneCommand.setURI(repoURL);
		cloneCommand.setTransportConfigCallback(CredentialsAuthenticator.getTransportConfigCallback(repoURL, npw));
		cloneCommand.setProgressMonitor(monitor);

		try (Git git = cloneCommand.call()) {
			setDefaultConfigSettings(git.getRepository());
		}
	}

	@Override
	public Iterable<PushResult> pushToRemote(UsernamePassword npw, ProgressMonitor monitor)
			throws IOException, GitAPIException {
		try (Git git = Git.open(getLocalRepositoryFolder())) {
			PushCommand pushCommand = git.push();
			pushCommand.setTransportConfigCallback(
					CredentialsAuthenticator.getTransportConfigCallback(getOnlineRepositoryURL(), npw));
			pushCommand.setProgressMonitor(monitor);

			Iterable<PushResult> result = pushCommand.call();

			// After a successful push, ensure we are tracking the current branch
			setTrackedBranch(git.getRepository(), git.getRepository().getBranch());

			return result;
		}
	}

	@Override
	public PullResult pullFromRemote(UsernamePassword npw, ProgressMonitor monitor)
			throws IOException, GitAPIException {
		try (Git git = Git.open(getLocalRepositoryFolder())) {
			PullCommand pullCommand = git.pull();
			pullCommand.setTransportConfigCallback(
					CredentialsAuthenticator.getTransportConfigCallback(getOnlineRepositoryURL(), npw));
			pullCommand.setRebase(false); // Merge, not rebase
			pullCommand.setProgressMonitor(monitor);
			return pullCommand.call();
		}
	}

	@Override
	public FetchResult fetchFromRemote(UsernamePassword npw, ProgressMonitor monitor, boolean isDryrun)
			throws IOException, GitAPIException {
		try (Git git = Git.open(getLocalRepositoryFolder())) {
			// Check and set tracked master branch
			setTrackedBranch(git.getRepository(), IGraficoConstants.MASTER);
			FetchCommand fetchCommand = git.fetch();
			fetchCommand.setTransportConfigCallback(
					CredentialsAuthenticator.getTransportConfigCallback(getOnlineRepositoryURL(), npw));
			fetchCommand.setProgressMonitor(monitor);
			fetchCommand.setDryRun(isDryrun);
			return fetchCommand.call();
		}
	}

	@Override
	public Git createNewLocalGitRepository(String URL) throws GitAPIException, IOException, URISyntaxException {
		if (getLocalRepositoryFolder().exists() && getLocalRepositoryFolder().list().length > 0) {
			throw new IOException("Directory: " + getLocalRepositoryFolder().getAbsolutePath() + " is not empty."); //$NON-NLS-1$ //$NON-NLS-2$
		}

		InitCommand initCommand = Git.init();
		initCommand.setDirectory(getLocalRepositoryFolder());
		Git git = initCommand.call();

		RemoteAddCommand remoteAddCommand = git.remoteAdd();
		remoteAddCommand.setName(IGraficoConstants.ORIGIN);
		remoteAddCommand.setUri(new URIish(URL));
		remoteAddCommand.call();

		setDefaultConfigSettings(git.getRepository());

		// Set tracked master branch
		setTrackedBranch(git.getRepository(), IGraficoConstants.MASTER);

		return git;
	}

	@Override
	public byte[] getFileContents(String path, String ref) throws IOException {
		byte[] bytes = null;

		try (Repository repository = Git.open(getLocalRepositoryFolder()).getRepository()) {
			ObjectId lastCommitId = repository.resolve(ref);

			try (RevWalk revWalk = new RevWalk(repository)) {
				RevCommit commit = revWalk.parseCommit(lastCommitId);
				RevTree tree = commit.getTree();

				// now try to find a specific file
				try (TreeWalk treeWalk = new TreeWalk(repository)) {
					treeWalk.addTree(tree);
					treeWalk.setRecursive(true);
					treeWalk.setFilter(PathFilter.create(path));

					// Not found, return null
					if (!treeWalk.next()) {
						return null;
					}

					ObjectId objectId = treeWalk.getObjectId(0);
					ObjectLoader loader = repository.open(objectId);

					bytes = loader.getBytes();
				}

				revWalk.dispose();
			}
		}

		return bytes;
	}

	@Override
	public String getWorkingTreeFileContents(String path) throws IOException {
		String str = ""; //$NON-NLS-1$

		try (Git git = Git.open(getLocalRepositoryFolder())) {
			try (BufferedReader in = new BufferedReader(new FileReader(new File(getLocalRepositoryFolder(), path)))) {
				String line;
				while ((line = in.readLine()) != null) {
					str += line + "\n"; //$NON-NLS-1$
				}
			}
		}

		return str;
	}

	@Override
	public void resetToRef(String ref) throws IOException, GitAPIException {
		// Check lock file is deleted
		checkDeleteLockFile();

		try (Git git = Git.open(getLocalRepositoryFolder())) {
			// Reset to master
			ResetCommand resetCommand = git.reset();
			resetCommand.setRef(ref);
			resetCommand.setMode(ResetType.HARD);
			resetCommand.call();

			// Clean extra files
			CleanCommand cleanCommand = git.clean();
			cleanCommand.setCleanDirectories(true);
			cleanCommand.call();
		}
	}

	@Override
	public boolean isHeadAndRemoteSame() throws IOException, GitAPIException {
		try (Repository repository = Git.open(getLocalRepositoryFolder()).getRepository()) {
			// Get remote branch ref
			BranchInfo currentRemoteBranch = getBranchStatus().getCurrentRemoteBranch();
			if (currentRemoteBranch == null) {
				return false;
			}

			// Remote
			Ref remoteRef = currentRemoteBranch.getRef();

			// Head
			Ref headRef = repository.findRef(HEAD);

			// In case of missing ref return false
			if (headRef == null || remoteRef == null) {
				return false;
			}

			return headRef.getObjectId().equals(remoteRef.getObjectId());
		}
	}

	@Override
	public void exportModelToGraficoFiles() throws IOException, GitAPIException {
		// Open the model before showing the progress monitor
		IArchimateModel model = IEditorModelManager.INSTANCE.openModel(getTempModelFile());

		if (model == null) {
			throw new IOException(Messages.ArchiRepository_0);
		}

		final Exception[] exception = new Exception[1];

		try {
			// When using this be careful that no UI operations are called as this could
			// lead to an SWT Invalid thread access exception
			// This will show a Cancel button which will not cancel, but this progress
			// monitor is the only one which does not freeze the UI
			PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {
				@Override
				public void run(IProgressMonitor pm) {
					pm.beginTask(Messages.ArchiRepository_1, IProgressMonitor.UNKNOWN);

					try {
						// Export
						GraficoModelExporter exporter = new GraficoModelExporter(model, getLocalRepositoryFolder());
						exporter.exportModel();

						// Check lock file is deleted
						checkDeleteLockFile();

						// Stage modified files to index - this can take a long time!
						// This will clear any different line endings and calls to git.status() will be
						// faster
						try (Git git = Git.open(getLocalRepositoryFolder())) {
							AddCommand addCommand = git.add();
							addCommand.addFilepattern("."); //$NON-NLS-1$
							addCommand.setUpdate(false);
							addCommand.call();
						}
					} catch (IOException | GitAPIException ex) {
						exception[0] = ex;
					}
				}
			});
		} catch (InvocationTargetException | InterruptedException ex) {
			throw new IOException(ex);
		}

		if (exception[0] instanceof IOException) {
			throw (IOException) exception[0];
		}
		if (exception[0] instanceof GitAPIException) {
			throw (GitAPIException) exception[0];
		}
	}

	@Override
	public PersonIdent getUserDetails() throws IOException {
		try (Git git = Git.open(getLocalRepositoryFolder())) {
			StoredConfig config = git.getRepository().getConfig();
			String name = StringUtils.safeString(
					config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME));
			String email = StringUtils.safeString(
					config.getString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL));
			return new PersonIdent(name, email);
		}
	}

	@Override
	public void saveUserDetails(String name, String email) throws IOException {
		// Get global user details from .gitconfig for comparison
		PersonIdent global = new PersonIdent("", ""); //$NON-NLS-1$ //$NON-NLS-2$

		try {
			global = GraficoUtils.getGitConfigUserDetails();
		} catch (ConfigInvalidException ex) {
			ex.printStackTrace();
		}

		// Save to local config
		try (Git git = Git.open(getLocalRepositoryFolder())) {
			StoredConfig config = git.getRepository().getConfig();

			// If global name == local name or blank then unset
			if (!StringUtils.isSet(name) || global.getName().equals(name)) {
				config.unset(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME);
			}
			// Set
			else {
				config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_NAME, name);
			}

			// If global email == local email or blank then unset
			if (!StringUtils.isSet(email) || global.getEmailAddress().equals(email)) {
				config.unset(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL);
			} else {
				config.setString(ConfigConstants.CONFIG_USER_SECTION, null, ConfigConstants.CONFIG_KEY_EMAIL, email);
			}

			config.save();
		}
	}

	@Override
	public boolean equals(Object obj) {
		if ((obj != null) && (obj instanceof ArchiRepository)) {
			return fLocalRepoFolder != null
					&& fLocalRepoFolder.equals(((IArchiRepository) obj).getLocalRepositoryFolder());
		}
		return false;
	}

	/**
	 * Set default settings in the config file
	 * 
	 * @param repository
	 * @throws IOException
	 */
	private void setDefaultConfigSettings(Repository repository) throws IOException {
		StoredConfig config = repository.getConfig();

		/*
		 * Set Line endings in the config file to autocrlf=input This ensures that files
		 * are not seen as different
		 */
		config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, ConfigConstants.CONFIG_KEY_AUTOCRLF, "input"); //$NON-NLS-1$

		/*
		 * Set longpaths=true because garbage collection is not possible otherwise See
		 * https://stackoverflow.com/questions/22575662/filename-too-long-in-git-for-
		 * windows
		 */
		config.setString(ConfigConstants.CONFIG_CORE_SECTION, null, "longpaths", "true"); //$NON-NLS-1$ //$NON-NLS-2$

		config.save();
	}

	/**
	 * Set the given branchName to track "origin"
	 */
	private void setTrackedBranch(Repository repository, String branchName) throws IOException {
		if (branchName == null) {
			return;
		}

		StoredConfig config = repository.getConfig();

		if (!IGraficoConstants.ORIGIN.equals(config.getString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName,
				ConfigConstants.CONFIG_KEY_REMOTE))) {
			config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_REMOTE,
					IGraficoConstants.ORIGIN);
			config.setString(ConfigConstants.CONFIG_BRANCH_SECTION, branchName, ConfigConstants.CONFIG_KEY_MERGE,
					Constants.R_HEADS + branchName);
			config.save();
		}
	}

	@Override
	public boolean hasLocalChanges() throws IOException {
		String latestChecksum = getLatestChecksum();
		if (latestChecksum == null) {
			return false;
		}

		String currentChecksum = createChecksum();
		return !latestChecksum.equals(currentChecksum);
	}

	@Override
	public boolean saveChecksum() throws IOException {
		// Get the file's checksum as string
		String checksum = createChecksum();
		if (checksum == null) {
			return false;
		}

		File checksumFile = new File(getLocalGitFolder(), "checksum"); //$NON-NLS-1$
		Files.write(Paths.get(checksumFile.getAbsolutePath()), checksum.getBytes(), StandardOpenOption.CREATE);

		return true;
	}

	@Override
	public BranchStatus getBranchStatus() throws IOException, GitAPIException {
		return new BranchStatus(this);
	}

	private String getLatestChecksum() throws IOException {
		File checksumFile = new File(getLocalGitFolder(), "checksum"); //$NON-NLS-1$
		if (!checksumFile.exists()) {
			return null;
		}

		byte[] bytes = Files.readAllBytes(Paths.get(checksumFile.getAbsolutePath()));
		return new String(bytes);
	}

	private String createChecksum() throws IOException {
		File tempFile = getTempModelFile();

		if (tempFile == null) {
			return null;
		}

		MessageDigest digest = null;
		try {
			digest = MessageDigest.getInstance("MD5"); //$NON-NLS-1$
		} catch (NoSuchAlgorithmException ex) {
			throw new IOException("NoSuchAlgorithm Exception", ex); //$NON-NLS-1$
		}

		// Get file input stream for reading the file content
		FileInputStream fis = new FileInputStream(tempFile);

		// Create byte array to read data in chunks
		byte[] byteArray = new byte[1024];
		int bytesCount = 0;

		// Read file data and update in message digest
		while ((bytesCount = fis.read(byteArray)) != -1) {
			digest.update(byteArray, 0, bytesCount);
		}

		fis.close();

		// Get the hash's bytes
		byte[] bytes = digest.digest();

		// This bytes[] has bytes in decimal format;
		// Convert it to hexadecimal format
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < bytes.length; i++) {
			sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
		}

		return sb.toString();
	}

	/**
	 * In some cases the lock file exists and leads to an error, so we delete it
	 */
	private void checkDeleteLockFile() {
		File lockFile = new File(getLocalGitFolder(), "index.lock"); //$NON-NLS-1$
		if (lockFile.exists() && lockFile.canWrite()) {
			lockFile.delete();
		}
	}
}

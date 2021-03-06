/*******************************************************************************
 * Copyright (c) 2017 Red Hat Inc..
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat Incorporated - initial API and implementation
 *******************************************************************************/

package com.redhat.fabric8analytics.lsp.eclipse.ui.internal;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URL;
import java.util.Map;
import java.util.HashMap ;
import java.util.Set;
import java.util.UUID;

import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.project.MavenProject;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.m2e.core.MavenPlugin;
import org.eclipse.m2e.core.project.IMavenProjectFacade;
import org.eclipse.m2e.core.project.IMavenProjectRegistry;
import org.eclipse.equinox.security.storage.StorageException;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IViewPart;

import com.redhat.fabric8analytics.lsp.eclipse.core.RecommenderAPIException;
import com.redhat.fabric8analytics.lsp.eclipse.core.RecommenderAPIProvider;
import com.redhat.fabric8analytics.lsp.eclipse.ui.EditorComposite;
import com.redhat.fabric8analytics.lsp.eclipse.ui.ExitHandler;
import com.redhat.fabric8analytics.lsp.eclipse.ui.StackAnalysesView;

public class AnalysesJobHandler extends Job{

	private static final int   TIMER_INTERVAL = 10000;

	private static String jobId = null;

	private String token;

	private RecommenderAPIProvider provider;

	private Boolean editorCheck;

	private String serverUrl;

	private Set<IFile> pomFiles;

	private String userKey;

	private String baseTempDir;

	public AnalysesJobHandler(String name, RecommenderAPIProvider provider, Boolean editorCheck, Set<IFile> pomFiles) {
		super(name);
		this.provider = provider;
		this.editorCheck = editorCheck;
		this.pomFiles = pomFiles;
	}

	protected IStatus run(IProgressMonitor monitor) {
		URL url;
		try {

			url = new URL("platform:/plugin/com.redhat.fabric8analytics.lsp.eclipse.ui/templates/index.html");
			url = FileLocator.toFileURL(url);
			IViewPart mainView = ExitHandler.getView();

			if(!editorCheck) {
				((StackAnalysesView) mainView).updatebrowserUrl(url.toString());
			}		

			else {
				EditorComposite.updateBrowser(url.toString());
			}
			Map<String, File> EffectivePomFiles = new HashMap<String,File>();


			for(IFile pomFile: pomFiles) {
				IMavenProjectRegistry  registry = MavenPlugin.getMavenProjectRegistry();
				IMavenProjectFacade facade = registry.create(pomFile, true, monitor);
				MavenProject mavenProject = facade.getMavenProject(monitor);
				StringWriter sw = new StringWriter();
				new MavenXpp3Writer().write(sw, mavenProject.getModel());

				String effectivePom = sw.toString();
				IProject currentProject = pomFile.getProject();
				baseTempDir = currentProject.getLocation() + "/target/fabrci8Temp/";
				String tempDir = baseTempDir + UUID.randomUUID().toString();
				Boolean tempFileExists = new File(tempDir.toString()).mkdirs();
				if(tempFileExists)
				{
					File effectivePomFile = new File(tempDir + "/pom.xml");
					FileWriter fw=new FileWriter(effectivePomFile);
					fw.write(effectivePom);
					fw.close();
					EffectivePomFiles.put(pomFile.getFullPath().toString(), effectivePomFile);
				}
			} 
			jobId = RecommenderAPIProvider.requestAnalyses(EffectivePomFiles);
			if (!provider.analysesFinished(jobId)) {
				schedule(TIMER_INTERVAL);	
			} else {
				syncWithUi(mainView);	
			}
			//			setTimerAnalyses();
			syncWithUi(mainView);

			deleteTempDir(baseTempDir);
		}catch (IOException | CoreException | RecommenderAPIException  e) {

			MessageDialogUtils.displayErrorMessage("Error while running stack analyses", e);
		}
		return Status.OK_STATUS;
	}

	private void setTimerAnalyses() throws StorageException {
		try {
			while(!provider.analysesFinished(jobId)){
				System.out.println("Still here");
				Thread.sleep(TIMER_INTERVAL);
			}
			System.out.println("Finished");
		} catch (RecommenderAPIException | InterruptedException e) {
			MessageDialogUtils.displayErrorMessage("Error while running stack analyses", e);
		}
	}

	private void deleteTempDir(String baseDir){
		File index = new File(baseDir);

		if(index.isDirectory()) {
			String[]entries = index.list();
			for(String s: entries){

				deleteTempDir(index.getAbsolutePath() + "/" + s);

			}
		}

		index.delete();
		return;
	}

	private void syncWithUi(IViewPart mainView) {
		Display.getDefault().asyncExec(new Runnable() {
			public void run() {
				if(editorCheck) {
					EditorComposite.updateBrowser(provider.getAnalysesURL(jobId));
					return;
				}

				((StackAnalysesView) mainView).updatebrowserUrl(provider.getAnalysesURL(jobId));
			}
		});

	}
}

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

package com.redhat.fabric8analytics.eclipse.ui.internal.view;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IProject;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.part.ViewPart;

public class StackAnalysesView extends ViewPart {

	public static final String NAME = "com.redhat.fabric8analytics.eclipse.ui.StackAnalysesView";

	private CTabFolder folder;
	private Map<List<IProject>, CTabItem> openAnalysis = new HashMap<>();

	@Override
	public void createPartControl(Composite parent) {
		folder = new CTabFolder(parent, SWT.BOTTOM);
	}

	public void openProjectAnalysis(List<IProject> projects, String browserUrl) {
		if (!openAnalysis.containsKey(projects)) {
			Display.getDefault().asyncExec(new Runnable() {
				
				@Override
				public void run() {
					CTabItem item = new CTabItem(folder, SWT.CLOSE);
					item.setText(projects.stream().map(IProject::getName).collect(Collectors.joining(",")));
					Browser browser = new Browser(folder, SWT.NONE);
					item.setControl(browser);
					item.addDisposeListener(new DisposeListener() {
						
						@Override
						public void widgetDisposed(DisposeEvent e) {
							openAnalysis.remove(projects);
						}
					});
					browser.setUrl(browserUrl);
					openAnalysis.put(projects, item);
					folder.setSelection(folder.indexOf(item));
				}
			});
		} else {
			CTabItem item = openAnalysis.get(projects);
			Browser browser = (Browser)item.getControl();
			Display.getDefault().asyncExec(new Runnable() {

				@Override
				public void run() {
					browser.setUrl(browserUrl);
					folder.setSelection(folder.indexOf(item));
				}
				
			});
		}
	}

	public void disposeBrowser(List<IProject> projects) {
		if (openAnalysis.containsKey(projects)) {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					CTabItem item = openAnalysis.get(projects);
					Browser browser = (Browser)item.getControl();
					if (!browser.isDisposed()) {
						browser.dispose();
					}
				}
			});
		}
	}

	@Override
	public void setFocus() {
		folder.setFocus();
	}

}

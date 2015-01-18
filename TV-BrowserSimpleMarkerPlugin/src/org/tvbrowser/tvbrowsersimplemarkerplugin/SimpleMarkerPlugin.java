/*
 * SimpleMarkerPlugin for TV-Browser for Android
 * Copyright (c) 2014 René Mach (rene@tvbrowser.org)
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software 
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.tvbrowser.tvbrowsersimplemarkerplugin;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.tvbrowser.devplugin.Channel;
import org.tvbrowser.devplugin.Plugin;
import org.tvbrowser.devplugin.PluginManager;
import org.tvbrowser.devplugin.PluginMenu;
import org.tvbrowser.devplugin.Program;
import org.tvbrowser.devplugin.ReceiveTarget;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

/**
 * A service class that provides a simple marking functionality for TV-Browser for Android.
 * 
 * @author René Mach
 */
public class SimpleMarkerPlugin extends Service {
  /* The id for the mark PluginMenu */
  private static final int MARK_ACTION = 1;
  /* The id for the unmark PluginMenu */
  private static final int UNMARK_ACTION = 2;
  
  /* The preferences key for the marking set */
  private static final String PREF_MARKINGS = "PREF_MARKINGS";
  
  /* The plugin manager of TV-Browser */
  private PluginManager mPluginManager;
  
  /* The set with the marking ids */
  private Set<String> mMarkingProgramIds;
    
  /**
   * At onBind the Plugin for TV-Browser is loaded.
   */
  @Override
  public IBinder onBind(Intent intent) {
    return getBinder;
  }
  
  @Override
  public boolean onUnbind(Intent intent) {
    /* Don't keep instance of plugin manager*/
    mPluginManager = null;
    
    stopSelf();
    
    return false;
  }
  
  @Override
  public void onDestroy() {
    /* Don't keep instance of plugin manager*/
    mPluginManager = null;
    
    super.onDestroy();
  }
  
  private void save() {
    Editor edit = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
    edit.remove(PREF_MARKINGS);
    edit.putString(PREF_MARKINGS, TextUtils.join(";", mMarkingProgramIds));
    edit.commit();
  }

  private Plugin.Stub getBinder = new Plugin.Stub() {
    private long mRemovingProgramId = -1;
    
    @Override
    public void openPreferences(List<Channel> subscribedChannels) throws RemoteException {}
    
    @Override
    public boolean onProgramContextMenuSelected(Program program, PluginMenu pluginMenu) throws RemoteException {      
      boolean mark = false;
      try {
      String programId = String.valueOf(program.getId());
      
      if(pluginMenu.getId() == MARK_ACTION) {
        if(!mMarkingProgramIds.contains(programId)) {
          mark = true;
          mMarkingProgramIds.add(programId);
          save();
        }
      }
      else {
        if(mMarkingProgramIds.contains(programId)) {
          mRemovingProgramId = program.getId();
          
          boolean unmarked = false;
          Log.d("info2", "get TVB SETTINGS ");
          Log.d("info2", "get TVB SETTINGS VERSION " + mPluginManager.getTvBrowserSettings().getTvbVersionCode() + " " + mPluginManager.getTvBrowserSettings().getLastKnownProgramId());
          if(mPluginManager.getTvBrowserSettings().getTvbVersionCode() >= 308) {
            Log.d("info2", "unmark now ");
            unmarked = mPluginManager.unmarkProgramWithIcon(program,SimpleMarkerPlugin.class.getCanonicalName());
            Log.d("info2", "unmark done " + unmarked);
          }
          else {
            unmarked = mPluginManager.unmarkProgram(program);
          }
          Log.d("info2", "OTHER UNMARK DONE " + unmarked);
          if(unmarked) {
            mMarkingProgramIds.remove(programId);
            save();
          }
          
          mRemovingProgramId = -1;
        }
      }
      }catch(Throwable t) {
        Log.d("info2", "",t);
      }
      return mark;
    }
    
    @Override
    public void onDeactivation() throws RemoteException {
      mPluginManager = null;
    }
    
    @Override
    public void onActivation(PluginManager pluginManager) throws RemoteException {
      mPluginManager = pluginManager;
      mPluginManager.unmarkProgram(null);
      mMarkingProgramIds = new HashSet<String>();

      Object test = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getAll().get(PREF_MARKINGS);
      
      if(test instanceof Set) {
        mMarkingProgramIds = (Set<String>)test;
      }
      else if(test instanceof String) {
        mMarkingProgramIds.addAll(Arrays.asList(((String)test).split(";")));
      }
    }
    
    @Override
    public boolean isMarked(long programId) throws RemoteException {
      return programId != mRemovingProgramId && mMarkingProgramIds.contains(String.valueOf(programId));
    }
    
    @Override
    public boolean hasPreferences() throws RemoteException {
      return false;
    }
    
    @Override
    public void handleFirstKnownProgramId(long programId) throws RemoteException {
      Log.d("info2", "firstKnown " + programId);
      if(programId == -1) {
        mMarkingProgramIds.clear();
      }
      else {
        long lastKnown = mPluginManager.getTvBrowserSettings().getLastKnownProgramId();
        Log.d("info2", "lastKnown " + lastKnown);
        String[] knownIds = mMarkingProgramIds.toArray(new String[mMarkingProgramIds.size()]);
        
        for(int i = knownIds.length-1; i >= 0; i--) {
          long id = Long.parseLong(knownIds[i]);
          if(id < programId || (lastKnown > programId && id > lastKnown)) {
            mMarkingProgramIds.remove(knownIds[i]);
          }
        }
      }
    }
    
    @Override
    public String getVersion() throws RemoteException {
      return getString(R.string.version);
    }
    
    @Override
    public String getName() throws RemoteException {
      return getString(R.string.service_simplemarker_name);
    }
    
    @Override
    public long[] getMarkedPrograms() throws RemoteException {
      long[] markings = new long[mMarkingProgramIds.size()];
      
      Iterator<String> values = mMarkingProgramIds.iterator();
      
      for(int i = 0; i < markings.length; i++) {
        markings[i] = Long.parseLong(values.next());
      }
      
      return markings;
    }
    
    @Override
    public byte[] getMarkIcon() throws RemoteException {
      Bitmap icon = BitmapFactory.decodeResource(getResources(), R.drawable.ic_action_attach);
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      icon.compress(Bitmap.CompressFormat.PNG, 100, stream);
      
      return stream.toByteArray();
    }
    
    @Override
    public String getLicense() throws RemoteException {
      return getString(R.string.license);
    }
    
    @Override
    public String getDescription() throws RemoteException {
      return getString(R.string.service_simplemarker_description);
    }
    
    @Override
    public PluginMenu[] getContextMenuActionsForProgram(Program program) throws RemoteException {
      PluginMenu menu = null;
      
      if(!mMarkingProgramIds.contains(String.valueOf(program.getId()))) {
        menu = new PluginMenu(MARK_ACTION, getString(R.string.service_simplemarker_context_menu_mark));
      }
      else {
        menu = new PluginMenu(UNMARK_ACTION, getString(R.string.service_simplemarker_context_menu_unmark));
      }
      
      return new PluginMenu[] {menu};
    }
    
    @Override
    public String getAuthor() throws RemoteException {
      return "René Mach";
    }
    
    @Override
    public ReceiveTarget[] getAvailableProgramReceiveTargets() throws RemoteException {
      return null;
    }

    @Override
    public void receivePrograms(Program[] programs, ReceiveTarget target) throws RemoteException {}
  };
}

package org.sonarlog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sonarlog.ioio.SonarReader;

import android.os.Bundle;
import android.os.PowerManager;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class SonarLoggerActivity extends Activity {
	 private Project prj = null;
	 private static final String TAG = "SonarLoggerActivity";
	 static final int DELETE_PROJECT_ID = 1;
	 public static int tot_pos;
	 public static int tot_depths;
	 public static int valid_pos;
	 public static int logged_depths;
	 public static double accuracy;
	 public static double distance;
	 public Context context;
	 private static volatile PowerManager.WakeLock lockStatic = null;
	 
	 private BroadcastReceiver updateUIReceiver = new BroadcastReceiver() {
		 public Runnable task = new Runnable() {
		     @Override
		     public void run() {
				 SonarLoggerActivity.this.updateStats();
		    }
		 };
		 @Override
		 public void onReceive(Context context, Intent intent) {
			 runOnUiThread(this.task);
		    }
	 };

	 synchronized private static PowerManager.WakeLock getLock(Context context) {
			if (lockStatic == null) {
				PowerManager mgr=
						(PowerManager)context.getSystemService(Context.POWER_SERVICE);
		      lockStatic=mgr.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
		    }
		    return(lockStatic);
		  }
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_sonar_logger);
		
		this.init(); // check that app dir exist, and default settings exist
		context = getApplicationContext();
	}
	
	public void onResume() {
		super.onResume();
		this.updateSpinner(); // List project directories
		this.loadProject(); // Set project directory to currently selected dir
		this.resumeStats();
		this.updateStats();

		LocalBroadcastManager.getInstance(this.context).registerReceiver(
				updateUIReceiver,
				new IntentFilter("UPDATE_STATS"));
		this.updateServiceSwitches();
	}
	
	public void init() {
		this.prj = new Project(SonarReader.projectName);
		if (this.prj.listProjects().length == 0)
			this.newProjectButtonClickHandler(null);

	}
	
	public void resumeStats() {
		tot_pos = 0;
		tot_depths = 0;
		valid_pos = 0;
		logged_depths = this.prj.get_logged_depths();
		accuracy = 999;
		distance = 999;

	}
	
	public void updateStats() {
		TextView textview;
		textview = (TextView) findViewById(R.id.textView_tot_pos);
		textview.setText((CharSequence) String.valueOf(tot_pos));
		textview = (TextView) findViewById(R.id.textView_valid_pos);
		textview.setText((CharSequence) String.valueOf(valid_pos));
		textview = (TextView) findViewById(R.id.total_depth_text_view);
		textview.setText((CharSequence) String.valueOf(tot_depths));
		textview = (TextView) findViewById(R.id.logged_depth_text_view);
		textview.setText((CharSequence) String.valueOf(logged_depths));
		textview = (TextView) findViewById(R.id.gps_accuracy);
		textview.setText((CharSequence) String.valueOf(accuracy));
		textview = (TextView) findViewById(R.id.distance);
		textview.setText((CharSequence) String.valueOf(distance));	
	}

	public void updateSpinner() {
		// Update spinner with project names
		String[] projects = this.prj.listProjects();
		List<String> projectList = new ArrayList<String>(Arrays.asList(projects));
		if (!projectList.contains(SonarReader.projectName))
			projectList.add(SonarReader.projectName);
		projects = projectList.toArray(new String[projectList.size()]);
						
		Spinner projectSpinner = (Spinner) findViewById(R.id.projectSpinner);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_dropdown_item,
				projects);
		//adapter.setDropDownViewResource
       // 	(android.R.layout.simple_spinner_dropdown_item); 
		projectSpinner.setAdapter(adapter);
		projectSpinner
			.setSelection(adapter.getPosition(SonarReader.projectName));

		
	}
	
	public void deleteProjectButtonClickHandler(View view) {
		DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				switch (which) {
				case DialogInterface.BUTTON_POSITIVE:
					// Yes button clicked
					Spinner projectSpinner = (Spinner) findViewById(R.id.projectSpinner);
					String projectName = (String) projectSpinner
							.getSelectedItem();
					if (projectName.equals("default")) {
						Toast.makeText(getBaseContext(),
								"Not allowed to delete default project",
								Toast.LENGTH_LONG).show();
						dialog.cancel();
						break;
					}
					if (projectName.equals(SonarLoggerActivity.this.prj.getProjectName())) {
						Toast.makeText(getBaseContext(),
								"Not allowed to delete active project",
								Toast.LENGTH_LONG).show();
						dialog.cancel();
						break;
					}
					SonarLoggerActivity.this.prj.deleteProject(projectName);
					SonarLoggerActivity.this.updateSpinner();
					SonarLoggerActivity.this.loadProject();
					dialog.cancel();
					break;
				case DialogInterface.BUTTON_NEGATIVE:
					// No button clicked
					dialog.cancel();
					break;
				}
			}
		};
		
		Spinner projectSpinner = (Spinner) findViewById(R.id.projectSpinner);
		String projectName = (String) projectSpinner
				.getSelectedItem();

		String msg = String.format("Delete project %s?", projectName);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(msg).setPositiveButton("Yes", dialogClickListener)
				.setNegativeButton("No", dialogClickListener).show();
		builder.create();
	}

	private void loadProject() {
		
		// Load configuration from project selected in spinner
		Spinner projectSpinner = (Spinner) findViewById(R.id.projectSpinner);
		String name = (String) projectSpinner.getSelectedItem();
		if (name == null)
			SonarReader.projectName = this.prj.get_current();
		else {
			SonarReader.projectName = name;
			this.prj.set_current();
		}
		this.prj = new Project(SonarReader.projectName);
		this.prj.read();
		this.setUIConfig();
	}
	
	public void runButtonClickHandler(View view) {
		CompoundButton toggleButton = (CompoundButton) findViewById(R.id.sonarToggleButton);
		Intent intent = new Intent(SonarLoggerActivity.this, SonarReader.class);
		if (toggleButton.isChecked()) {
			this.getUIConfig();
			this.prj.write();
			getLock(this).acquire();
			startService(intent);
		} else {
			if (lockStatic.isHeld()) {
				lockStatic.release();
			}
			stopService(intent);
		}
	}

	public void newProjectButtonClickHandler(View view) {
		// Create new project dir with a copy of .settings.txt
		Editable projectNameEdit = ((EditText) findViewById(R.id.newProjectEditText))
				.getEditableText();
		SonarReader.projectName = projectNameEdit.toString();			
		this.updateSpinner();
		projectNameEdit.clear();
		this.loadProject();
		}

	public void loadProjectButtonClickHandler(View view) {
		// Click handler for load project button
		CompoundButton toggleButton = (CompoundButton) findViewById(R.id.sonarToggleButton);
		if(toggleButton.isChecked()) {
			Toast.makeText(getBaseContext(),
					"Cannot load project while service is running",
					Toast.LENGTH_LONG).show();
		} else
			this.loadProject();
			this.resumeStats();
			this.updateStats();
	}
	
	public void checkBoxClickHandler(View view) {
		CheckBox append_log_checkbox = (CheckBox) view;
		this.prj.setBoolean("log.append",
					append_log_checkbox.isChecked());
	}
	
	public void onDestroy() {
		super.onDestroy();
			
		//bind to running services
		CompoundButton toggleButton = (CompoundButton) findViewById(R.id.sonarToggleButton);		
		Intent intent = new Intent(SonarLoggerActivity.this, SonarReader.class);
		if(toggleButton.isChecked()) {
			stopService(intent);
			Log.d(TAG, "SonarLogger stopped");
		}
	}
	
	public void onPause() {
		super.onPause();
   	    LocalBroadcastManager.getInstance(this.context).unregisterReceiver(updateUIReceiver);
	}
	
	public void updateServiceSwitches() {
		CompoundButton toggleButton = (CompoundButton) findViewById(R.id.sonarToggleButton);
		if (!toggleButton.isChecked() && this.sonarServiceIsRunning())
			toggleButton.setChecked(true);
		else {
			if (toggleButton.isChecked() && !this.sonarServiceIsRunning()) 
				toggleButton.setChecked(false);
		}
	}

	private boolean sonarServiceIsRunning() {
	    ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
	    for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
	        if (SonarReader.class.getName().equals(service.service.getClassName())) {
	            return true;
	        }
	    }
	    return false;
	}
	public void setUIConfig() {
		// set checkBoxes
		((CheckBox) findViewById(R.id.appendCheckBox)).setChecked(
				this.prj.getParameterAsBoolean("log.append"));

		// set floats
		View gps_acc =  findViewById(R.id.gpsAccuracyEditText);
		
		String acc_string = this.prj.getParameterAsString("gps.accuracy");
		((EditText) gps_acc).setText( acc_string);
		
		// set integers

		// set booleans
		View append_log_checkbox = findViewById(R.id.appendCheckBox);
		boolean append_logs = this.prj.getParameterAsBoolean("log.append");
		((CheckBox) append_log_checkbox).setChecked(append_logs);
		
	}

	public void getUIConfig() {
		// get checkBoxes
		// get doubles
		double gps_accuracy = 25;

		String gps_accuracy_string = String.valueOf(
				((EditText) findViewById(
						R.id.gpsAccuracyEditText)).getEditableText());
		try {
		 gps_accuracy = Double.parseDouble( gps_accuracy_string);
		 this.prj.setDouble("gps.accuracy", gps_accuracy);
		}
		catch (NumberFormatException e) {
			Toast.makeText(getBaseContext(),
					"Invalid value for gps accuracy, should be float",
					Toast.LENGTH_LONG).show();
		}
		
		boolean append_logs = Boolean.valueOf(((CheckBox) findViewById(
				R.id.appendCheckBox)).isChecked());
		this.prj.setBoolean("log.append", append_logs);
				
		
		// get integers
	}


}

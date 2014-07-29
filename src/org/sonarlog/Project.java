package org.sonarlog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.sonarlog.ioio.SonarReading;

import android.annotation.SuppressLint;
import android.os.Environment;
import android.util.Log;

public class Project {
	private static final String SETTINGS_FILE_NAME = "settings.rf";
	private static final String SETTINGS_TEMPLATE_FILE_NAME = ".settings.rf";
	private static final String CURRENT_PROJECT_FILE_NAME = ".current_project.rf";
	private static final String DEPTHLOG_FILE_NAME = "depth.csv";
	private static final String ROOT_DIR_NAME = "sonarlogger";
	
	@SuppressLint("SimpleDateFormat")
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private final String TAG = "Settings";	
	private File rf = null;
	private File rootDir = null;
	private String projectName = null;
	private File template = null;
	private File current_project = null;
	private File depthlog_file = null;
	private long last_read = 0;
	private BufferedWriter depthlog;
	
	private Map<String, String> parameters = new HashMap<String, String>();
	// Map with row order for settings incl. comment rows
	private Map<String, String> rfRowNr = null;
	
	public Project(String projName) {
		if(!this.isExternalStoragePresent()) {
			Log.e("TAG", "Sdcard is not available, terminating");
			System.exit(1);
		}
		this.rootDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), ROOT_DIR_NAME);
		this.projectName = projName;
		this.rf = new File(this.getProjectDir(), SETTINGS_FILE_NAME);
		this.template = new File(this.rootDir, SETTINGS_TEMPLATE_FILE_NAME);
		this.current_project = new File(this.rootDir, CURRENT_PROJECT_FILE_NAME);

		if (!this.rootDir.exists())
			this.rootDir.mkdir();
		
		if (!this.template.exists())
			this.write_template();

		if (!this.current_project.exists())
			this.write_current_project("default");
		
		File projectDir = this.getProjectDir();
		if (!projectDir.exists())
			projectDir.mkdir();
		
		if (!this.rf.exists())
			this.copy_settings_template();
		
		this.read();
	}
	
	public void copy_settings_template() {
		try {
			FileInputStream in = new FileInputStream(this.template);
			FileOutputStream out = new FileOutputStream(this.rf);
			byte[] buf = new byte[1024];
			int i = 0;
			while ((i = in.read(buf)) != -1) {
				out.write(buf, 0, i);
			}
			in.close();
			out.close();
		} catch (IOException e) {
			Log.e(TAG,"Error copying project resource file");
		}
	}
	
	public String[] listProjects() {
		// List project directories
		ArrayList<String> projectDirs = new ArrayList<String>();
		projectDirs.add("default");
		File[] files = this.rootDir.listFiles();
		for (int i = 0; i < files.length; i++)
			if (files[i].isDirectory())
				projectDirs.add(files[i].getName());
		String[] projectNames = new String[projectDirs.size()];
		if (!projectDirs.isEmpty())
			projectDirs.toArray(projectNames);
		return projectNames;
	}
	
	public boolean settings_updated() {
		return (this.rf.lastModified() > this.last_read);
	}
	
	public void set_current() {
		if(this.current_project.exists())
			this.current_project.delete();
		this.write_current_project(this.projectName);
	}
	
	public String get_current() {
		BufferedReader reader = null;		
		Log.d(TAG,"Reading resources");
		try {
			reader = new BufferedReader(new FileReader(current_project));
			String name = reader.readLine();
			return name;
		}
		catch (FileNotFoundException e) {
			Log.e(TAG, "Error: " + e.getMessage());
			System.exit(1);
		}
		catch (IOException e) {
			Log.e(TAG, "Error: " + e.getMessage());
			System.exit(1);
		} 
		finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
				Log.e(TAG, "Error: " + e.getMessage());
				System.exit(1);
			}
		}
		return "default";

	}
	
	public void write_current_project(String name) {
		BufferedWriter writer = null;
		Log.d(TAG,"Creating current project file");
		
		try {
			if(!current_project.createNewFile())
				Log.e(TAG, "Could not create .current_project.rf");
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
		try {
			if(!template.canWrite())
				Log.e(TAG, "Cannot write to .current_project.rf");
			
			writer = new BufferedWriter(new FileWriter(current_project));
			writer.write(name);
		} catch (FileNotFoundException e) {
			Log.e(TAG, "Error: " + e.getMessage());
			System.exit(1);
		} catch (IOException e) {
			Log.e(TAG, "Error: " + e.getMessage());
			System.exit(1);
		} finally {
			try {
				if (writer != null) {
					writer.close();
				}
			} catch (IOException e) {
				Log.e(TAG, "Error: " + e.getMessage());
				System.exit(1);
			}
		}
	}
	
	
	public void write_template() {
		BufferedWriter writer = null;
		Log.d(TAG,"Creating default settings");
		
		try {
			if(!template.createNewFile())
				Log.e(TAG, "Could not create default .settings.rf");
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}
		try {
			if(!template.canWrite())
				Log.e(TAG, "Cannot write to .settings.rf");
			
			writer = new BufferedWriter(new FileWriter(template));
			writer.write("#Sonar logger, resource file\n");
			writer.write("#Required precision to use position\n");
			writer.write("gps.accuracy: 5\n");
			writer.write("#Logging settings\n");
			writer.write("log.append: true\n");

		} catch (FileNotFoundException e) {
			Log.e(TAG, "Error: " + e.getMessage());
			System.exit(1);
		} catch (IOException e) {
			Log.e(TAG, "Error: " + e.getMessage());
			System.exit(1);
		} finally {
			try {
				if (writer != null) {
					writer.close();
				}
			} catch (IOException e) {
				Log.e(TAG, "Error: " + e.getMessage());
				System.exit(1);
			}
		}
	}
	
	public void initLogs() {

		// update resources for remote control params
		boolean append = this.getParameterAsBoolean("log.append");

		try {
			// Create file
			depthlog_file = new File(this.getProjectDir(), DEPTHLOG_FILE_NAME);
			if (!depthlog_file.exists())
				append = false; 
			FileWriter depthlogfstream = new FileWriter(depthlog_file, append);
			this.depthlog = new BufferedWriter(depthlogfstream);			
			if (!append)
				this.depthlog.write("timestamp\tlon\tlat\tdepth\taccuracy");
				this.depthlog.newLine();
		} catch (Exception e) {// Catch exception if any
			Log.e(TAG, "Could not open log to append" + e.getMessage());
			System.exit(1);
		}
	}
	
	public void log_depth(SonarReading reading) {
		Date readingDate = new Date(reading.timestamp);
		this.log("depth",
				String.format("%s;%f;%f;%f;%f",
						DATE_FORMAT.format(readingDate),
						reading.longitude,
						reading.latitude,
						reading.depth,
						reading.accuracy)
		);
		

	}
	
	public int get_logged_depths(){
		FileInputStream stream;
		depthlog_file = new File(this.getProjectDir(), DEPTHLOG_FILE_NAME);
		try {
			stream = new FileInputStream(depthlog_file);
		} catch (FileNotFoundException e) {
			return 0;
		}
		byte[] buffer = new byte[8192];
		int count = 0;
		int n;
		
		try {
			while ((n = stream.read(buffer)) > 0) {
				for (int i = 0; i < n; i++) {
					if (buffer[i] == '\n') count++;
				}
			}
			stream.close();
		} catch (IOException e) {
			return 0;
		}
		return count - 1;
	}


	public void read() {
		// Read resource file into settings hash map
		rfRowNr = new HashMap<String, String>();
		parameters = new HashMap<String, String>();

		BufferedReader reader = null;
		
		Log.d(TAG,"Reading resources");
		try {
			reader = new BufferedReader(new FileReader(rf));
			String row;
			int rownr = 0;
			while ((row = reader.readLine()) != null) {
				if (row.startsWith("#") || row.trim() == "") {
					rfRowNr.put(String.valueOf(rownr), row);
					rownr += 1;
					continue;
				}
				String[] keyValuePair = row.split(":");
				// For rfRowNr, rownumber is key and value is settings key-word
				rfRowNr.put(String.valueOf(rownr), keyValuePair[0]);
				parameters.put(keyValuePair[0], keyValuePair[1].trim());
				rownr += 1;
			}
		}
		catch (FileNotFoundException e) {
			Log.e(TAG, "Error: " + e.getMessage());
			System.exit(1);
		}
		catch (IOException e) {
			Log.e(TAG, "Error: " + e.getMessage());
			System.exit(1);
		} 
		finally {
			try {
				if (reader != null) {
					reader.close();
				}
			} catch (IOException e) {
				Log.e(TAG, "Error: " + e.getMessage());
				System.exit(1);
			}
		}
		
		this.last_read = this.rf.lastModified();
	}

	public void write() {
		// resource file object
		try {
			this.rf.createNewFile();
		}
		catch (Exception e) {
			Log.e(TAG, "Could not init settings.rf for writing: " + e.getMessage());
		}
		
		BufferedWriter writer = null;
		Log.d(TAG,"Writing resources");
		try {
			writer = new BufferedWriter(new FileWriter(this.rf));
			for (int i = 0; i < rfRowNr.size(); i++) {
				String row = rfRowNr.get(String.valueOf(i));
				if (row.trim().startsWith("#") || row.trim() == "") {
					writer.write(row + "\n");
				} else {
					String val = parameters.get(row);
					writer.write(String.format("%s: %s\n", row, val));
				}
			}
		} catch (FileNotFoundException e) {
			Log.e(TAG, "Error: " + e.getMessage());
			System.exit(1);
		} catch (IOException e) {
			Log.e(TAG, "Error: " + e.getMessage());
			System.exit(1);
		} finally {
			try {
				if (writer != null) {
					writer.close();
				}
			} catch (IOException e) {
				Log.e(TAG, "Error: " + e.getMessage());
				System.exit(1);
			}
		}
	}
	
	public void setInt(String key, int value) {
		this.parameters.put(key, String.valueOf(value));
	}
	
	public void setDouble(String key, double value) {
		this.parameters.put(key, String.valueOf(value));
	}
	
	public void setString(String key, String value) {
		this.parameters.put(key, value);
	}
	
	public void setBoolean(String key, boolean value) {
		this.parameters.put(key, String.valueOf(value));
	}

	public double getParameterAsDouble(String par) {
		double val = 0;
		try {
			val = Double.valueOf(parameters.get(par));
		} catch (Exception e) {
			Log.e(TAG,"Error: could not parse double "
					+ par + " in resource file");
			System.exit(1);
		}
		return val;
	}
	
	public String getParameterAsString(String par) {
		return parameters.get(par);
	}
	
	public int getParameterAsInt(String par) {
		int val = 0;
		try {
			val = Integer.valueOf(parameters.get(par));
		} catch (Exception e) {
			Log.e(TAG,"Error: could not parse integer "
					+ par + " in resource file");
			System.exit(1);
		}
		return val;
	}

	public boolean getParameterAsBoolean(String par) {
		boolean val = false;
		try {
			val = Boolean.valueOf(parameters.get(par));
		} catch (Exception e) {
			Log.e(TAG,"Error: could not parse boolean of "
					+ par + " in resource file");
			System.exit(1);
		}
		return val;
	}
	
	public boolean containsKey(String key) {
		return parameters.containsKey(key);
	}
	
	public File getRootDir() {
		return this.rootDir;
	}
	
	public File getProjectDir() {
		try {
			if (this.projectName.equals("default")) {
				Log.d(TAG, "Project dir is rootDir");
				return this.rootDir;
			}
			else {
				Log.d(TAG, "Project dir is " + this.projectName);
				return new File(this.rootDir, this.projectName);			
			}
		}
		catch (NullPointerException e) {
			Log.d(TAG, "big trouble");
		}
		return this.rootDir;

	}
	
	public String getProjectName() {
		return this.projectName;
	}
	
	public void deleteProject(String projName) {
		if (projName.equals(this.projectName))
			Log.e(TAG, "Cannot delete active project");
		else if (projName.equals("default"))
			Log.e(TAG, "Cannot delete default project");
		else {
			File selectedProject = new File(
					this.getRootDir(),
						projName);
			String[] children = selectedProject.list();
			if(children != null) {
				for (int i = 0; i < children.length; i++) {
					new File(selectedProject, children[i]).delete();
				}
			}
			selectedProject.delete();
		}
	}
		
	private boolean isExternalStoragePresent() {
		boolean mExternalStorageAvailable = false;
		boolean mExternalStorageWriteable = false;
		String state = Environment.getExternalStorageState();

		if (Environment.MEDIA_MOUNTED.equals(state)) {
			// We can read and write the media
			mExternalStorageAvailable = mExternalStorageWriteable = true;
		} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			// We can only read the media
			mExternalStorageAvailable = true;
			mExternalStorageWriteable = false;
		} else {
			// Something else is wrong. It may be one of many other states, but
			// all we need
			// to know is we can neither read nor write
			mExternalStorageAvailable = mExternalStorageWriteable = false;
		}
		if (!((mExternalStorageAvailable) && (mExternalStorageWriteable))) {
			Log.d(TAG, String.format(
					"SD card available: %s, writable: %s",
					String.valueOf(mExternalStorageAvailable),
					String.valueOf(mExternalStorageWriteable)));
			return false;
		}
		return true;
	}
	

	public void log(String logName, String logStr) {
		try {
			if (logName == "depth") {
				this.depthlog.write(logStr);
				this.depthlog.newLine();
				this.depthlog.flush();
			}
			else
				Log.e(TAG, "Undefined logger: " + logName);
		} catch (IOException e) {
			Log.e(TAG, "Caught IOException: " + e.getMessage());
		}

	}
		
	public void close() {
		try {
			this.depthlog.close();
		} catch (IOException e) {
			Log.e(TAG, "Could not close logs: " + e.getMessage());
		}
	}
}


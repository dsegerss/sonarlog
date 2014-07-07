package org.sonarlog.ioio;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;

import org.sonarlog.SonarLoggerActivity;

import android.util.Log;

public class NMEAParser {
	String SENTENCE = "DPT";
	
	public static final String TAG = "NMEAParser";

	protected StringBuilder __buffer = null;
	
	public Reader reader = null;
	
	public NMEAParser(InputStream instream)
    {
            __buffer = new StringBuilder();
            reader = new InputStreamReader(instream);
    }
	
	public void handleCharacters()
            throws IOException {
        int r;
        if (reader.ready()) {
        	while ((r = reader.read()) != -1) {
        		char chr = (char) r;
        		__buffer.append( chr );
        		if ( chr == '\n' )
        		{
        			if (parseSentence(__buffer.toString() )) {
        				__buffer = new StringBuilder();
        				break;
        			} else {
        				__buffer = new StringBuilder();
        			}	
        		}
        	}
        }
        
    }
	
	protected boolean parseSentence( String sentence ) 
	{
		//Log.i(TAG, sentence);
		String[] words = sentence.split( "," );
		if (words.length >= 3) {
			try {
				String key = words[0].substring(3);
				if (key.equals("DPT")) {
					SonarLoggerActivity.tot_depths += 1;
					SonarReader.depths.add(
							new SonarReading(
									Double.valueOf(words[1]),
									System.currentTimeMillis())
							);
					return true;
					}
				else
					return false;
			} 
			catch  (IndexOutOfBoundsException e) {
				Log.e(TAG, e.getMessage());
				return false;
			}
		}
		else
			return false;
	}
}	

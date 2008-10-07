package test.ccn.data.read;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;

import org.junit.Test;

import test.ccn.endtoend.BaseLibrarySource;

import com.parc.ccn.data.CompleteName;
import com.parc.ccn.data.ContentObject;
import com.parc.ccn.data.query.CCNInterestListener;
import com.parc.ccn.data.query.Interest;

/**
 * 
 * @author rasmusse
 * 
 * NOTE: This test requires ccnd to be running
 *
 */

public class ReadTest extends BaseLibrarySource implements CCNInterestListener {

	public ReadTest() throws Throwable {
		super();
	}
	
	@Test
	public void getNext() throws Throwable {
		System.out.println("getNext test started");
		for (int i = 0; i < count; i++) {
			Thread.sleep(rand.nextInt(50));
			library.put("/getNext/" + Integer.toString(i), Integer.toString(count - i));
		}
		System.out.println("Put sequence finished");
		for (int i = 0; i < count; i++) {
			Thread.sleep(rand.nextInt(50));
			int tValue = rand.nextInt(count - 1);
			ArrayList<ContentObject> results = library.getNext("/getNext/" + new Integer(tValue).toString(), 
					Integer.toString(count - tValue), 1);
			for (ContentObject result : results) {
				checkResult(result, tValue + 1);
			}
		}
		System.out.println("getNext test finished");
	}
	
	@Test
	public void getLatest() throws Throwable {
		int highest = 0;
		System.out.println("getLatest test started");
		for (int i = 0; i < count; i++) {
			Thread.sleep(rand.nextInt(50));
			int tValue = getRandomFromSet(count, false);
			if (tValue > highest)
				highest = tValue;
			library.put("/getLatest/" + Integer.toString(tValue), Integer.toString(tValue));
			Thread.sleep(rand.nextInt(50));
			ArrayList<ContentObject> results = library.getLatest("/getLatest/" + Integer.toString(tValue), 1);
			for (ContentObject result : results) {
				checkResult(result, highest);
			}
		}
		System.out.println("getLatest test finished");
	}

	public void addInterest(Interest interest) {}

	public void cancelInterests() {}

	public Interest[] getInterests() {
		return null;
	}

	public Interest handleContent(ArrayList<ContentObject> results) {
		return null;
	}

	public void interestTimedOut(Interest interest) {}

	public boolean matchesInterest(CompleteName name) {
		return false;
	}
	
	private void checkResult(ContentObject result, int value) {
		String resultAsString = result.name().toString();
		int sep = resultAsString.lastIndexOf('/');
		assertTrue(sep > 0);
		int resultValue = Integer.parseInt(resultAsString.substring(sep + 1));
		assertEquals(new Integer(value), new Integer(resultValue));
	}

}
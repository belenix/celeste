package sunlabs.beehive;

public class Release {
	private static String thisRelease = null;
	
	public static String ThisRevision() {
		if (Release.thisRelease != null)
			return Release.thisRelease;
		Release.thisRelease = Release.class.getPackage().getImplementationVersion();
		if (Release.thisRelease == null) {
			Release.thisRelease = "Revision: Working copy.";			
		}
		return Release.thisRelease;
	}
}

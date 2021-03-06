package uk.co.harcourtprogramming.docitten;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import uk.co.harcourtprogramming.internetrelaycats.RelayCat;

/**
 * <p>Recursive URL retriever</p>
 */
public class LinkResolver extends Thread
{

	/**
	 * <p>Thread group for running link resolvers in</p.
	 * <p>The group is marked as a Daemon group, and so will be ignored by the
	 * JVM when it comes to determining the number of important running threads</p>
	 */
	private final static ThreadGroup THREAD_GROUP = new ThreadGroup("LinkResolvers") {
		@Override
		public void uncaughtException(Thread t, Throwable e)
		{
			LOG.log(Level.SEVERE, "Excpetion in " + t.getName(), e);
		}
	};

	private final static Logger LOG = Logger.getLogger("DoCitten.LinkService");
	private final static Pattern PROTOCOL = Pattern.compile("^https?://.+");
	private final static int TIMEOUT = 2000;
	private final static int MAX_HOPS = 5;

	/**
	 * <p>Letters for binary prefixs</p>
	 * <p>kilo, mega, giga, terra, pera, exa, zetta, yotta, hella</p>
	 * <p>Note: hella is my favourite proposal for 10^27. Also, Long.MAX_VALUE
	 * is only about 8 EiB, so it'll be a little while before it gets used</p>
	 */
	private final static String UNIT_PREFIX = "kMGTPEZYH";
	/**
	 * ln(ratio between any two prefixes)
	 */
	private final static double UNIT_SIZE = Math.log(1024);
	/**
	 * Converts a byte count into a 1dp figure of &lt;kMG...&gt;iB (uses base 1024)
	 * @param bytes the number of bytes
	 * @return formatted value
	 */
	private static String humanReadableByteCount(long bytes) {
		if (bytes < 1024) return bytes + " B";
		int exp = (int) (Math.log(bytes) / UNIT_SIZE);
		return String.format("%.1f %siB", bytes / Math.pow(1024, exp), UNIT_PREFIX.charAt(exp-1));
	}

	/**
	 * The original URI that we are retrieving
	 */
	private final URI baseURI;
	/**
	 * Message that we will be replying to
	 */
	private final RelayCat mess;
	private final String target;

	/**
	 * Creates a link resolver instance
	 * @param baseURI the link we're following
	 * @param mess the message to replyToAll
	 */
	public LinkResolver(String baseURI, RelayCat mess, String target)
	{
		if (!PROTOCOL.matcher(baseURI).matches())
		{
			this.baseURI = URI.create("http://" + baseURI);
		}
		else
		{
			this.baseURI = URI.create(baseURI);
		}
		this.mess = mess;
		this.target = target;
		setDaemon(true);
	}

	/**
	 * Runs this LinkResolver
	 */
	@Override
	public void run()
	{
		try
		{
			URL curr = baseURI.toURL();
			HttpURLConnection conn;
			boolean resolved = false;
			int hops = 0;
			while (true)
			{
				conn = (HttpURLConnection)curr.openConnection();
				conn.setInstanceFollowRedirects(false);
				conn.setRequestMethod("HEAD");
				conn.setConnectTimeout(TIMEOUT);
				conn.setReadTimeout(TIMEOUT);
				conn.connect();
				switch (conn.getResponseCode())
				{
					case HttpURLConnection.HTTP_ACCEPTED:
					case HttpURLConnection.HTTP_CREATED:
					case HttpURLConnection.HTTP_NO_CONTENT:
					case HttpURLConnection.HTTP_OK:
					case HttpURLConnection.HTTP_PARTIAL:
					case HttpURLConnection.HTTP_RESET:
					case HttpURLConnection.HTTP_NOT_MODIFIED:
						resolved = true;
						break;
					case HttpURLConnection.HTTP_MOVED_PERM:
					case HttpURLConnection.HTTP_MOVED_TEMP:
					case HttpURLConnection.HTTP_MULT_CHOICE:
					case HttpURLConnection.HTTP_SEE_OTHER:
						if (conn.getHeaderField("Location") == null) return;
						curr = URI.create(curr.toExternalForm()).resolve(conn.getHeaderField("Location")).toURL();
						break;
					default:
						return;
				}
				conn.disconnect();
				if (interrupted()) return;
				if (resolved) break;
				++hops;
				if (hops == MAX_HOPS) break;
			}

			if (hops == MAX_HOPS)
			{
				mess.message(target,
					String.format("[%s] (Unresolved after %d hops)", curr.getHost(), MAX_HOPS));
				return;
			}

			conn = (HttpURLConnection)curr.openConnection();
			conn.setRequestMethod("GET");
			conn.connect();

			String mime = conn.getContentType();
			if (mime == null) mime = "";
			mime = mime.split(";")[0];

			if (conn.getContentType().matches("(text/.+|.+xhtml.+)"))
			{
				mess.message(target,
					String.format("[%s] %s", curr.getHost(), getTitle(conn)));
			}
			else
			{
				if (conn.getContentLength() == -1)
				{
					mess.message(target,
						String.format("[%s] %s (size unknown)", curr.getHost(),
						mime));
				}
				else
				{
					mess.message(target,
						String.format("[%s] %s %s", curr.getHost(), mime,
						humanReadableByteCount(conn.getContentLength())));
				}
			}
		}
		catch (Throwable ex)
		{
			throw new RuntimeException(ex);
		}
	}

	private String getTitle(HttpURLConnection conn) throws IOException
	{
		BufferedReader pageData = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		String line;
		boolean reading = false;
		String title = "[No Title Set]";
		while (true)
		{
			line = pageData.readLine();
			if (line == null) break;
			if (line.contains("<title>"))
			{
				reading = true;
				line = line.substring(line.indexOf("<title>") + 7);
				title = "";
			}
			if (reading && line.contains("</title>"))
			{
				title += line.substring(0, line.indexOf("</title>"));
				break;
			}
			if (line.contains("</head>") || line.contains("<body>")) break;
			if (reading) title += line;
		}
		pageData.close();
		return title.trim().replaceAll("\\s\\s+", " ");
	}

}

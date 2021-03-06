package uk.co.harcourtprogramming.docitten;

import org.junit.Test;
import static org.junit.Assert.*;
import uk.co.harcourtprogramming.internetrelaycats.RelayCat;

public class TitleFinderTest
{
	private final String uri = "http://www.artima.com/weblogs/viewpost.jsp?thread=142428";
	private final String title = "Java API Design Guidelines";
	private final String site = "www.artima.com";
	private final String expected = String.format("[%s] %s", site, title);

	private String target = null;
	private String message = null;

	private final RelayCat cat = new RelayCat() {
		@Override
		public void message(String target, String message)
		{
			TitleFinderTest.this.target  = target;
			TitleFinderTest.this.message = message;
		}

		@Override
		public void act(String target, String message)
		{
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public void join(String channel)
		{
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public void leave(String channel)
		{
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public String getNick()
		{
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public String[] names(String channel)
		{
			throw new UnsupportedOperationException("Not supported yet.");
		}

		@Override
		public String[] channels()
		{
			throw new UnsupportedOperationException("Not supported yet.");
		}
	};

	@Test
	@SuppressWarnings("CallToThreadRun")
	public void TestTitleResolution() throws InterruptedException
	{
		final String nick = "bob";

		LinkResolver r = new LinkResolver(uri, cat, nick);

		r.run();

		assertNotNull(target);
		assertEquals(nick, target);
		assertNotNull(message);
		assertEquals(expected, message);
	}
}

package com.bleedingwolf.ratpack;


class RatpackRunner {
	RatpackApp app = new RatpackApp()

	void run(File scriptFile) {
    app.prepareScriptForExecutionOnApp(scriptFile)
		RatpackServlet.serve(app)
	}

    static void main(String[] args)
    {
        def runner = new RatpackRunner()
        runner.run(new File(args[0]))
    }
}

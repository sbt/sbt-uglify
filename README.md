sbt-uglify
==========

[![Build Status](https://github.com/sbt/sbt-uglify/actions/workflows/build-test.yml/badge.svg)](https://github.com/sbt/sbt-uglify/actions/workflows/build-test.yml)

An sbt-web plugin to perform [UglifyJS optimization](https://github.com/mishoo/UglifyJS2) on the asset pipeline.

Usage
-----
To use this plugin, use the `addSbtPlugin` command within your project's `project/plugins.sbt` file:

```scala
addSbtPlugin("com.github.sbt" % "sbt-uglify" % "3.0.0")
```

Your project's build file also needs to enable sbt-web plugins. For example, with `build.sbt`:

```scala
lazy val root = (project in file(".")).enablePlugins(SbtWeb)
```

As with all sbt-web asset pipeline plugins you must declare their order of execution:

```scala
pipelineStages := Seq(uglify)
```

A standard build profile for the Uglify optimizer is provided which will mangle variables for obfuscation and
compression. Each input `.js` file found in your assets folders will have a corresponding `.min.js` file and source maps will also be generated.

## includeFilter

If you wish to limit or extend what is uglified then you can use filters:
```scala
uglify / includeFilter := GlobFilter("myjs/*.js"),
```
...where the above will include only those files under the `myjs` folder.

The sbt `excludeFilter` is also available to the `uglify` scope and defaults to excluding the public folder and extracted Webjars.

## uglifyOps

If you wish to change how files are mapped from input to output, you can change the `uglifyOps` setting to point at another grouping.

The default ops takes a source file and produces minified file and source map:
```scala
uglifyOps := UglifyOps.singleFileWithSourceMapOut
```

This ops takes a source file and produces minified file only (no source map):
```scala
uglifyOps := UglifyOps.singleFile
```

This ops takes a source file and source map and produces minified file and combined source map. Your includeFilter must include source map files for this to work:
```scala
uglifyOps := UglifyOps.singleFileWithSourceMapInAndOut
```

## Settings
You are able to use and/or customize settings already made, and add your own. Here are a list of relevant settings and
their meanings (please refer to the [UglifyJS documentation](https://github.com/mishoo/UglifyJS2) for details on the
options):

Option                  | Description                                                                                   | Default
------------------------|-----------------------------------------------------------------------------------------------|----------
uglifyComments          | Specifies comments handling.                                                                  | `None`
uglifyCompress          | Enables compression. Set true to compress.                                                    | `true`
uglifyCompressOptions   | A sequence of options for compression such as hoist_vars, if_return etc.                      | `Nil`
uglifyDefine            | Define globals.                                                                               | `None`
uglifyEnclose           | Enclose in one big function.                                                                  | `false`
uglifyIncludeSource     | Include the content of source files in the source map as the sourcesContent property.         | `false`
uglifyMangle            | Enables name mangling.                                                                        | `true`
uglifyMangleOptions     | Options for mangling such as sort, topLevel etc.                                              | `Nil`
uglifyPreamble          | Any preamble to include at the start of the output.                                           | `None`
uglifyReserved          | Reserved names to exclude from mangling.                                                      | `Nil`
uglifyOps               | A function defining how to combine input files into output files.                             | `UglifyOps.singleFileWithSourceMapOut`

The plugin is built on top of [JavaScript Engine](https://github.com/sbt/sbt-js-engine) which supports different JavaScript runtimes.

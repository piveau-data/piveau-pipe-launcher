# piveau pipe launcher
Library for pipe actions in a piveau cluster.

## Features
 * Internal management of pipes
 * Convenient API
 * Vert.x support

## Table of Contents
1. [Build & Install](#build-and-install)
1. [Get started](#get-started)
1. [Cluster configuration](#cluster-configuration)
1. [License](#license)

## Build and Install
Requirements:
 * Git
 * Maven
 * Java

```bash
$ git clone https://github.com/piveau-data/piveau-pipe-launcher.git
$ cd piveau-pipe-launcher
$ mvn install
```

## Use
Add dependency to your project pom file:
```xml
<dependency>
    <groupId>io.piveau</groupId>
    <artifactId>pipe-launcher</artifactId>
    <version>1.0.1</version>
</dependency>
```

## Get started

The launcher works in a piveau cluster. Therefore, the first step is to create and [configure](#cluster-configuration) a piveau cluster object.
```java
Future<PiveauCluster> future = PiveauCluster.init(vertx, config);
future.setHandler(ar -> {
    if (ar.succeded()) {
        ...
    } else {
        ...
    }
});
``` 
`config` is a JSON object defining pipe repos and service discovery. See [Cluster configuration](#cluster-configuration) for details.

Then retrieve the pipe launcher associated to this cluster
```java
PipeLauncher launcher = cluster.pipeLauncher();
``` 
To get a list of all available pipes:
```java
List<Pipe> pipes = launcher.availablePipes();
```
And finally, launch pipes:
```java
// run pipe simply by name
Future<Void> future1 = launcher.runPipe("mypipe");
future.setHandler(ar -> {
    if (ar.succeded()) {
        ...
    } else {
        ...
    }
});

// run pipe by name with initial data
Future<Void> future1 = launcher.runPipeWithData("mypipe", data, mimeType)
future.setHandler(ar -> {
    if (ar.succeded()) {
        ...
    } else {
        ...
    }
});
```

You can run pipes with and without payload data, binary or string data, with or without specific segment configurations, and more. The launcher interface will provide more convenient methods in next releases.

## Cluster configuration
The current release allows to configure pipe repositories and a very simple service discovery mechanism based on a manually defined map of service names to endpoints
```json
{
  "pipeRepositories": {
    ...
  },
  "serviceDiscovery": {
    ...
  }
}
```

### Pipe repositories
Pipe repositories are a map of named locations for json files. The repo named `resources` should contain paths loaded from the classpath, all others are treated as git repositories. 
```json
"pipeRepositories": {
  "resources": {
    "paths": [ "pipes" ]
  },
  "system": {
    "uri": "",
    "branch": "master",
    "username": "",
    "token": ""
  }
}
``` 


### Service discovery
The current service discovery is a JSON map of segment names to endpoints. Currently `http` and `eventbus` endpoints are supported.
```json
"serviceDiscovery": {
  "test1-segment": {
    "http": {
      "method": "POST",
      "address": "http://example.com:8080/pipe"
    },
    "eventbus": {
      "address": "piveau.pipe.test.queue"
    } 
  },
  "test2-segment": {
    ...
  }
}
```

## License

[Apache License, Version 2.0](LICENSE.md)
  
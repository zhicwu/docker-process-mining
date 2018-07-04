print('// ');
print('// Initializing...');
print('// ');

const boot = Java.type('org.processmining.framework.boot.Boot');
const logLevel = boot.Level.ALL;

function getPackagesToInstall(manager) {
    const list = new java.util.ArrayList();

    manager.getAvailablePackages().forEach(function (d) {
        const installed = manager.findInstalledVersion(d);

        if (manager.isAvailable(d) &&
            (installed == null || installed.getVersion().lessThan(d.getVersion()))) {
            list.add(d);
        }
    });

    return list;
}

boot.setReleaseInstalled('', '');

const manager = Java.type('org.processmining.framework.packages.PackageManager').getInstance();

manager.initialize(logLevel);

print('// ');
print('// Installing/Upgrading packages which may take a while...');
print('// ');

manager.update(false, logLevel);
manager.install(getPackagesToInstall(manager));

boot.setLatestReleaseInstalled();

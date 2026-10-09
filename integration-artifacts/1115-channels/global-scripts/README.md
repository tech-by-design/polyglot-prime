# Mirth Connect Global Scripts

This folder contains `global-script.xml`, the export of the Mirth Connect **Global Scripts** used alongside the [1115 Mirth Connect channels](../README.md).

---

## 📄 Overview

Global scripts run across every channel deployed on the Mirth Connect server. The export is a `<map>` with one entry per script type:

| Script | When it runs | Current behavior |
|--------|--------------|------------------|
| `Deploy` | Once per deploy or redeploy task | No-op (`return;`) |
| `Undeploy` | Once per deploy/undeploy/redeploy task when at least one channel was undeployed | No-op (`return;`) |
| `Preprocessor` | Before every message, on every channel | Passes the message through unchanged (`return message;`) |
| `Postprocessor` | After every message, on every channel | No-op (`return;`) |

All four scripts are the Mirth Connect defaults. Channel-specific logic lives in each channel's own deploy, preprocessor and transformer scripts.

---

## 🚀 Import

In the Mirth Connect Administrator, open **Channels → Edit Global Scripts → Import Scripts** and select `global-script.xml`.

---

## 🔗 Related Documentation

- [1115 Channels (Mirth Connect)](../README.md)
- [Integration Artifacts Index](../../README.md)

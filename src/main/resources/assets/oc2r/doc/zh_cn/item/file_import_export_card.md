# 文件导入/导出卡
![打破次元壁！](item:oc2r:file_import_export_card)

文件导入/导出卡能打破第四面墙。它可以：
- 从您现实中的电脑上传文件到虚拟[电脑](../block/computer.md)。
- 从虚拟电脑下载文件到您现实中的电脑。

为了方便起见，默认的Linux发行版提供了两个用于这些操作的实用脚本，`import.lua`和`export.lua`。

`import.lua`在运行时会提示您选择一个文件上传到虚拟电脑。文件将存储在当前工作目录中。如果存在一个与导入文件同名的文件，会提供重命名文件的选项。

`export.lua`接收虚拟电脑中的文件路径作为参数。它将此文件下载到您的真实电脑，并提供一个保存对话框，让您选择保存下载文件的位置或取消操作。

两个脚本都会提示所有正在与电脑终端交互的用户。对于上传操作（`import.lua`），将使用第一个上传的文件，其他客户端的提示将被取消。对于下载操作，所有客户端都将被提供保存导出文件的选项。
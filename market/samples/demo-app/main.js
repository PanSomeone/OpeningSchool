// 开源大学 · 示例应用模块（API v2）
// 演示：生命周期 apply/dispose、本地存储、主页小组件、插件页面、动作与事件。

function apply(ctx) {
  var count = parseInt(ctx.storage.get("count", "0"), 10);

  function view() {
    return {
      type: "column",
      spacing: 6,
      children: [
        { type: "text", text: "示例计数器", size: "small", emphasis: "secondary" },
        { type: "text", text: String(count), size: "title", emphasis: "primary" },
        {
          type: "row",
          spacing: 8,
          children: [
            { type: "button", text: "加一", action: "inc" },
            { type: "button", text: "清零", action: "reset" }
          ]
        }
      ]
    };
  }

  function render() {
    ctx.ui.homeWidget({
      id: "counter",
      title: "计数器",
      iconKey: "star",
      columns: 1,
      rows: 1,
      view: view()
    });
    ctx.ui.screen({
      title: "计数器",
      iconKey: "star",
      view: {
        type: "column",
        spacing: 12,
        children: [
          {
            type: "card",
            children: view().children
          },
          { type: "divider" },
          { type: "text", text: "这是插件自己声明的页面，由宿主用原生组件渲染。", size: "small", emphasis: "secondary" }
        ]
      }
    });
  }

  ctx.events.on("action:inc", function () {
    count += 1;
    ctx.storage.set("count", String(count));
    render();
  });

  ctx.events.on("action:reset", function () {
    count = 0;
    ctx.storage.set("count", "0");
    render();
  });

  ctx.events.on("timetable.updated", function () {
    ctx.log("课表已更新，计数仍为 " + count);
  });

  ctx.dispose(function () {
    ctx.log("示例计数器已卸载");
  });

  render();
}

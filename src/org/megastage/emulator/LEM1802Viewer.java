package org.megastage.emulator;

import java.awt.*;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Timer;
import java.util.TimerTask;

public class LEM1802Viewer {
    public LEMCanvas canvas = new LEMCanvas();
    public VirtualMonitor vm;
    BufferedImage img2;
    BufferedImage img;
    int[] pixels;
    protected boolean keepAlive = true;
    GraphicsConfiguration config;

    public LEM1802Viewer() {
        canvas.setPreferredSize(new Dimension(2*144, 2*112));
        canvas.setMinimumSize(new Dimension(144, 112));
        //canvas.setMaximumSize(new Dimension(144, 112));
        canvas.setFocusable(true);
        canvas.requestFocus();

        GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice device = env.getDefaultScreenDevice();
        config = device.getDefaultConfiguration();

        img2 = fuck(144, 112, 2);
        img = fuck(128, 112, 2);
        pixels = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();

    }

    public BufferedImage fuck(int w, int h, int x) {
        return config.createCompatibleImage(w, h, Transparency.TRANSLUCENT);
    }

    public VirtualMonitor attach(VirtualMonitor vm) {
        if (this.vm != null) {
            detach();
        }
        this.vm = vm;
        return vm;
    }

    public VirtualMonitor detach() {
        if (vm != null) {
            VirtualMonitor vm = this.vm;
            vm.setPixels(null);
            this.vm = null;
            return vm;
        }
        return null;
    }

    public void die() {
        keepAlive = false;
        canvas.stop();
    }

    public class LEMCanvas extends Canvas {
        private static final long serialVersionUID = 1L;
        private BufferStrategy strategy;
        private TimerTask renderTask;
        private Timer timer = new Timer();

        public LEMCanvas() {
            this.setIgnoreRepaint(true);
        }

        public synchronized void render() {
            try {
                if (isDisplayable()) {
                    // clear background
                    Graphics2D bkG = (Graphics2D) strategy.getDrawGraphics();
                    bkG.setColor(new Color(240, 240, 240));
                    bkG.fillRect(0, 0, getWidth(), getHeight());

                    if (vm != null) {
                        vm.setPixels(pixels);
                        vm.render();
                        int iScale = (getWidth() - 1) / 144 + 1;
                        iScale = (getHeight() - 1) / 112 + 1 < iScale ? (getHeight() - 1) / 112 + 1 : iScale;
                        iScale = iScale > 0 ? iScale : 1;
                        img2 = fuck(144 * iScale, 112 * iScale, 2);
                        Graphics2D g = (Graphics2D) img2.getGraphics();
                        if (g != null) {
                            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                            g.setColor(new Color(pixels[0x3000]));
                            g.fillRect(0, 0, 144 * iScale, 112 * iScale);
                            g.drawImage(img, 8 * iScale, 8 * iScale, 128 * iScale, 112 * iScale, null);
                            g.dispose();
                        }

                        if (bkG != null) {
                            bkG.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                            bkG.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
                            float scale;
                            if ((float) getWidth() / (float) getHeight() > 144f / 112f) {
                                scale = (float) getHeight() / 112f;
                            } else {
                                scale = (float) getWidth() / 144f;
                            }
                            int w = (int) (144 * scale);
                            int h = (int) (112 * scale);
                            bkG.drawImage(img2, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
                        }
                    }
                    bkG.dispose();
                    if (!keepAlive) {
                        return;
                    }
                    strategy.show();
                }
                //Toolkit.getDefaultToolkit().sync();
            } catch (IllegalStateException e) {
                //TODO Make this not happen on application close? Meh.
            }
        }

        public void setup() {
            this.createBufferStrategy(2);
            strategy = this.getBufferStrategy();
            start();
        }

        public void start() {
            if (renderTask != null) {
                renderTask.cancel();
            }
            renderTask = new TimerTask() {
                @Override
                public void run() {
                    render();
                }
            };
            timer.schedule(renderTask, 0, 16);
        }

        protected void stop() {
            renderTask.cancel();
        }

        @Override
        public void paint(Graphics g) {
            render();
        }
    }
}

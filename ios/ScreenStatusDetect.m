#import "ScreenStatusDetect.h"

@implementation ScreenStatusDetect

- (NSArray<NSString *> *)supportedEvents
{
    return @[@"screenStatusChange"];
}

- (instancetype)init {
    if (self = [super init]) {
        if (@available(iOS 11.0, *)) {
            [[NSNotificationCenter defaultCenter] addObserver:self
                                                     selector:@selector(screenStatusChange)
                                                         name:UIScreenCapturedDidChangeNotification
                                                       object:nil];
            [[NSNotificationCenter defaultCenter] addObserver:self
                                                     selector:@selector(screenStatusChangeForScreenshot)
                                                         name:UIApplicationUserDidTakeScreenshotNotification
                                                       object:nil];
            [self addListener:@"screenStatusChange"];
        } else {
            // Fallback on earlier versions
        }
    }

    return self;
}

+ (BOOL)requiresMainQueueSetup {
  return YES;
}

-(void)screenStatusChangeForScreenshot {
    [self sendEventWithName:@"screenStatusChange" body:@{@"screenStatus": @"TAKE_SCREENSHOT"}];
}

-(void)screenStatusChange {
    NSString *status = [self screenStatus];

    [self sendEventWithName:@"screenStatusChange" body:@{@"screenStatus": status}];
}

- (NSString *)screenStatus {
    for (UIScreen *screen in UIScreen.screens) {
        if ([screen respondsToSelector:@selector(isCaptured)]) {
            // iOS 11+ has isCaptured method.
            if ([screen performSelector:@selector(isCaptured)]) {
                return @"SCREEN_CAPTURE"; // screen capture is active
            } else if (screen.mirroredScreen) {
                return @"SCREEN_MIRRORING"; // mirroring is active
            }
        } else {
            // iOS version below 11.0
            if (screen.mirroredScreen){
                return @"SCREEN_MIRRORING";
            }
        }
    }

    return @"SCREEN_NORMAL";
}

RCT_EXPORT_MODULE();

RCT_REMAP_METHOD(getCurrentStatus, resolver:(RCTPromiseResolveBlock)resolve rejecter:(RCTPromiseRejectBlock)reject)
{
    NSString *status = [self screenStatus];
    resolve(@{@"screenStatus": status});
}

@end
